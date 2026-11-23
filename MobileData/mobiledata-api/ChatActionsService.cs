using System.Collections.Concurrent;
using System.Globalization;
using System.Text;
using System.Text.RegularExpressions;
using Microsoft.Data.SqlClient;
using System.Data;

namespace MobileData.Api;

// AI khong duoc phep tu tao SQL hay sua tai khoan. Cac lenh nhay cam chi di qua service nay.
public sealed record ChatOption(string Label, string Action, long TargetId);
public sealed record ChatPlan(long Id, string Code, string Name, string Carrier,
    decimal Price, decimal DataGb, string DataType, int Days, bool CanRegister);
public sealed record ChatReply(string Reply, string HandledBy, bool CanHandoff,
    IReadOnlyList<ChatOption>? Options = null, string? ConfirmationId = null,
    bool HandoffSuggested = false, IReadOnlyList<ChatPlan>? Plans = null);
public sealed record ChatActionRequest(string? Action, long TargetId);
public sealed record ChatConfirmRequest(string? ConfirmationId);

public sealed class ChatActionsService
{
    private sealed record Pending(int AccountId, int SubscriberId, string Action,
        long TargetId, DateTimeOffset Expires);
    private readonly ConcurrentDictionary<string, Pending> _pending = new();
    private readonly MobileDataRepository _repository;
    private readonly MobileDataUserService _user;
    private readonly OllamaChatService _ai;
    private readonly ChatConversationMemory _memory;
    private readonly ChatRecommendationState _recommendations;
    private readonly ChatIntentRouter _intentRouter;
    private readonly string _connectionString;
    public ChatActionsService(MobileDataRepository repository, MobileDataUserService user,
        OllamaChatService ai, ChatConversationMemory memory, ChatRecommendationState recommendations,
        ChatIntentRouter intentRouter, IConfiguration configuration)
    {
        _repository = repository; _user = user; _ai = ai; _memory = memory;
        _recommendations = recommendations;
        _intentRouter = intentRouter;
        _connectionString = configuration.GetConnectionString("MobileDataDB")
            ?? throw new InvalidOperationException("Thiếu cấu hình MobileDataDB.");
    }

    private static string Fold(string text)
    {
        var normalized = text.ToLowerInvariant().Normalize(NormalizationForm.FormD);
        var result = new StringBuilder();
        foreach (var ch in normalized)
            if (CharUnicodeInfo.GetUnicodeCategory(ch) != UnicodeCategory.NonSpacingMark)
                result.Append(ch == 'đ' ? 'd' : ch);
        return result.ToString();
    }

    // Lay nha mang theo thue bao DA XAC THUC, khong nhan ma nha mang tu client.
    private async Task<string?> MyCarrierAsync(int subscriberId, CancellationToken ct)
    {
        const string sql = "SELECT MaNhaMang FROM dbo.ThueBao WHERE MaThueBao = @SubscriberId AND TrangThai = 'ACTIVE'";
        await using var db = new SqlConnection(_connectionString);
        await db.OpenAsync(ct);
        await using var cmd = new SqlCommand(sql, db);
        cmd.Parameters.Add("@SubscriberId", SqlDbType.Int).Value = subscriberId;
        return (await cmd.ExecuteScalarAsync(ct)) as string;
    }

    private static string[] ExplicitCarriers(string s)
    {
        var result = new List<string>();
        if (s.Contains("viettel")) result.Add("viettel");
        if (s.Contains("vinaphone") || s.Contains("vina phone")) result.Add("vinaphone");
        if (s.Contains("mobifone") || s.Contains("mobi fone")) result.Add("mobifone");
        return result.ToArray();
    }

    private static decimal? ReadBudget(string s, out bool strict)
    {
        strict = false;
        var match = Regex.Match(s,
            @"(?:duoi|nho hon|toi da|khong qua|khong vuot|<=|<|tam|khoang|gia)\s*(\d{1,9}(?:[.,]\d{3})*)\s*(k|nghin|ngan|d|dong)?\b",
            RegexOptions.IgnoreCase);
        if (!match.Success) return null;
        string raw = match.Groups[1].Value.Replace(".", "").Replace(",", "");
        if (!decimal.TryParse(raw, NumberStyles.None, CultureInfo.InvariantCulture, out decimal value)) return null;
        if (match.Groups[2].Value is "k" or "nghin" or "ngan") value *= 1000;
        strict = match.Value.StartsWith("duoi") || match.Value.StartsWith("nho hon") ||
            (match.Value.StartsWith("<") && !match.Value.StartsWith("<="));
        return value is >= 1000 and <= 100000000 ? value : null;
    }

    private static bool RefersToSelf(string s) =>
        s.Contains("cua toi") || s.Contains("thue bao cua minh") ||
        s.Contains("so cua toi") || s.Contains("toi dang dung") || s.Contains("toi dang su dung") ||
        s.Contains("thue bao hien tai") || s.Contains("phu hop voi thue bao") ||
        s.Contains("phu hop cho thue bao") || s.Contains("so cua minh");

    private static bool RefersToOther(string s) =>
        s.Contains("me toi") || s.Contains("bo toi") || s.Contains("ba toi") || s.Contains("ban toi") ||
        s.Contains("nguoi quen") || s.Contains("cho nguoi khac") || s.Contains("anh toi") ||
        s.Contains("chi toi") || s.Contains("em toi") || s.Contains("cho me") || s.Contains("cho ban");

    // Quy trinh: AI phan tich JSON -> backend kiem tra dieu kien -> API CSDL co san -> phan hoi.
    // Moi nhanh deu luu ngu canh de 'roi toi phai lam gi?' khong bi hieu thanh cau hoi moi.
    public async Task<ChatReply> MessageAsync(string text, int subscriberId, CancellationToken ct)
    {
        var history = _memory.Read(subscriberId);
        string s = Fold(text);
        ChatReply Done(ChatReply result)
        {
            _memory.Append(subscriberId, text, result.Reply);
            return result;
        }

        var intent = await _intentRouter.ResolveAsync(text, history, ct);
        // Nguoi dung tra loi 'Viettel' sau cau hoi lam ro nha mang cua nguoi than.
        var clarification = _recommendations.Get(subscriberId);
        bool answeredCarrier = clarification?.NeedsCarrier == true &&
            (s.Contains("viettel") || s.Contains("vinaphone") || s.Contains("mobi fone") || s.Contains("mobifone")) &&
            !Has(s, "goi", "data", "dang ky", "huy", "nap tien", "so du", "lich su", "khieu nai");
        if (answeredCarrier && (intent.Kind is ChatIntentKind.General or ChatIntentKind.Plans))
        {
            var selected = s.Contains("viettel") ? "viettel" : s.Contains("vinaphone") ? "vinaphone" : "mobifone";
            intent = intent with { Kind = ChatIntentKind.Plans, Target = "other", Carriers = new[] { selected }, FollowUp = true };
        }
        if (intent.Kind == ChatIntentKind.ChangeBalance)
            return Done(new ChatReply("Tôi không có quyền tự tăng hay chỉnh sửa số dư tài khoản. " +
                "Bạn có thể sử dụng chức năng Nạp tiền mô phỏng trong MobileData; mọi thay đổi phải qua quy trình của hệ thống.",
                "SYSTEM", true));
        if (intent.Kind == ChatIntentKind.BuyForOther)
            return Done(new ChatReply("Tôi có thể tư vấn gói cho người thân hoặc bạn bè, nhưng không thể đăng ký " +
                "trên thuê bao của người khác bằng phiên đăng nhập hiện tại. " +
                "Người sở hữu thuê bao cần đăng nhập và xác nhận giao dịch trên tài khoản phù hợp. " +
                "Bạn muốn tôi tìm gói để tham khảo trước không?", "SYSTEM", true));
        if (intent.Kind == ChatIntentKind.Complaint)
        {
            _recommendations.Clear(subscriberId);
            return Done(new ChatReply(ComplaintAnswer(intent.Reason, intent.Brief), "SYSTEM", true,
                HandoffSuggested: true));
        }
        if (intent.Kind == ChatIntentKind.Support)
        {
            _recommendations.Clear(subscriberId);
            return Done(new ChatReply("Tôi có thể chuyển cuộc trò chuyện đến quản trị viên. " +
                "Bạn hãy nhấn nút chuyển và xác nhận để tạo yêu cầu hỗ trợ. Tôi không tự gửi khi bạn chưa đồng ý.",
                "SYSTEM", true, HandoffSuggested: true));
        }
        if (intent.Kind == ChatIntentKind.Balance)
        {
            var balance = await _user.BalanceAsync(subscriberId, ct);
            return Done(new ChatReply($"Số dư hiện tại: {balance:N0}đ (dữ liệu mô phỏng).", "SYSTEM", true));
        }
        if (intent.Kind == ChatIntentKind.History)
        {
            var events = (await _user.HistoryAsync(subscriberId, ct)).Take(5).ToArray();
            if (events.Length == 0) return Done(new ChatReply("Bạn chưa có lịch sử giao dịch.", "SYSTEM", true));
            return Done(new ChatReply("Giao dịch gần đây:\n" + string.Join("\n",
                events.Select(x => $"• {x.At:dd/MM/yyyy}: {x.Type}, {x.Amount:N0}đ, {x.Status}")) +
                "\nNếu có giao dịch bất thường, bạn có thể yêu cầu quản trị viên kiểm tra.", "SYSTEM", true));
        }
        if (intent.Kind == ChatIntentKind.Active)
        {
            var active = await _user.ActiveAsync(subscriberId, ct);
            if (active.Count == 0) return Done(new ChatReply("Bạn chưa có gói data còn hiệu lực.", "SYSTEM", true));
            return Done(new ChatReply("Gói bạn đang sử dụng:\n" +
                string.Join("\n", active.Take(8).Select(x => $"• {x.Code} – {x.Name}; hết hạn {x.ExpiresAt:dd/MM/yyyy HH:mm}")) +
                "\nChỉ hủy gói khi bạn đã xác nhận.", "SYSTEM", true,
                active.Take(8).Select(x => new ChatOption("Hủy " + x.Code, "cancel", x.RegistrationId)).ToArray()));
        }

        if (intent.Kind == ChatIntentKind.Plans)
        {
            if (intent.ResetFilters) _recommendations.Clear(subscriberId);
            var prior = intent.ResetFilters ? null : _recommendations.Get(subscriberId);
            // Ke thua dieu kien chi khi co dau hieu hoi tiep; mot cau moi khong duoc mac dinh giu filter cu.
            bool followup = prior is not null && !intent.ResetFilters &&
                (answeredCarrier || (intent.FollowUp && Has(s, "the neu", "the con", "vay con", "con cho toi", "loai khac", "re hon", "lon hon", "nhieu hon", "thoi thi")));
            string[] carriers = intent.Carriers;
            bool forSelf = intent.Target == "self";
            bool forOther = intent.Target == "other";
            bool compare = intent.Target == "compare";

            if (followup && carriers.Length == 0 && intent.Target == "unspecified")
            {
                carriers = prior!.Carriers;
                forSelf = prior.ForSelf;
            }
            if (forSelf && carriers.Length == 0)
            {
                string? ownCarrier = await MyCarrierAsync(subscriberId, ct);
                carriers = ownCarrier is null ? Array.Empty<string>() : new[] { Fold(ownCarrier) switch
                {
                    "vt" => "viettel", "vnp" => "vinaphone", "mbf" => "mobifone", var x => x
                }};
            }
            if (forOther && intent.UnknownCarrier && carriers.Length == 0)
            {
                _recommendations.Set(subscriberId, new PlanSearchState(Array.Empty<string>(),
                    intent.ExactDays, intent.MinDays, intent.MaxDays, intent.MinPrice, intent.MaxPrice,
                    intent.MaxExclusive, false, intent.PreferData, null, NeedsCarrier: true));
                return Done(new ChatReply("Tôi có thể tư vấn cho người thân của bạn. " +
                    "Bạn cho biết thuê bao đó dùng Viettel, VinaPhone hay MobiFone nhé. " +
                    "Nếu chưa rõ, tôi có thể so sánh các nhà mạng để bạn tham khảo trước.", "SYSTEM", true));
            }

            int? exactDays = intent.ExactDays ?? (followup ? prior?.ExactDays : null);
            int? minDays = intent.MinDays ?? (followup && intent.ExactDays is null ? prior?.MinDays : null);
            int? maxDays = intent.MaxDays ?? (followup && intent.ExactDays is null ? prior?.MaxDays : null);
            decimal? minPrice = intent.MinPrice ?? (followup ? prior?.MinPrice : null);
            decimal? maxPrice = intent.MaxPrice ?? (followup ? prior?.MaxPrice : null);
            bool maxExclusive = intent.MaxPrice.HasValue ? intent.MaxExclusive : followup && prior?.MaxExclusive == true;
            bool preferData = intent.PreferData || (followup && prior?.PreferData == true);
            if (intent.ExactDays.HasValue) { minDays = null; maxDays = null; }
            if (intent.MinDays.HasValue || intent.MaxDays.HasValue) exactDays = null;

            // Neu khong co dieu kien ngay/gia, quay ve danh muc chung; khong giu gia tri cua cau truoc.
            var all = await _repository.GetPlansAsync(null, null, ct);
            var eligible = all.Where(x => carriers.Length == 0 || carriers.Any(c =>
                Fold(x.CarrierName).Contains(c) || Fold(x.CarrierCode) == c));
            if (minPrice.HasValue) eligible = eligible.Where(x => x.Price >= minPrice.Value);
            if (maxPrice.HasValue) eligible = eligible.Where(x => maxExclusive ? x.Price < maxPrice.Value : x.Price <= maxPrice.Value);
            if (minDays.HasValue) eligible = eligible.Where(x => x.DurationDays >= minDays.Value);
            if (maxDays.HasValue) eligible = eligible.Where(x => x.DurationDays <= maxDays.Value);
            var pool = eligible.ToList();

            bool alternativeShorter = false, alternativeNearest = false;
            List<PlanDto> plans;
            if (exactDays.HasValue)
            {
                plans = pool.Where(x => x.DurationDays == exactDays.Value).ToList();
                if (plans.Count == 0 && intent.AllowShorter)
                {
                    plans = pool.Where(x => x.DurationDays < exactDays.Value).ToList();
                    alternativeShorter = plans.Count > 0;
                }
                if (plans.Count == 0 && intent.ApproximateDays && pool.Count > 0)
                {
                    int nearest = pool.Min(x => Math.Abs(x.DurationDays - exactDays.Value));
                    plans = pool.Where(x => Math.Abs(x.DurationDays - exactDays.Value) == nearest).ToList();
                    alternativeNearest = true;
                }
            }
            else plans = pool;

            if (followup && Has(s, "dung luong lon hon", "dung luong nhieu hon") &&
                prior?.LastBestDataGb is decimal previousGb)
                plans = plans.Where(x => x.DataGb > previousGb).ToList();

            bool noConstraints = carriers.Length == 0 && !exactDays.HasValue && !minDays.HasValue && !maxDays.HasValue &&
                !minPrice.HasValue && !maxPrice.HasValue;
            if (noConstraints)
                plans = plans.OrderByDescending(x => x.IsPopular).ThenBy(x => x.Price).ThenByDescending(x => x.DurationDays).Take(5).ToList();
            else if (alternativeShorter)
                plans = (preferData ? plans.OrderByDescending(x => x.DataGb).ThenByDescending(x => x.DurationDays)
                    : plans.OrderByDescending(x => x.DurationDays).ThenByDescending(x => x.DataGb)).ThenBy(x => x.Price).Take(5).ToList();
            else if (alternativeNearest)
                plans = plans.OrderBy(x => Math.Abs(x.DurationDays - exactDays!.Value)).ThenBy(x => x.Price).Take(5).ToList();
            else
                plans = (preferData ? plans.OrderByDescending(x => x.DataGb).ThenBy(x => x.Price)
                    : plans.OrderBy(x => x.Price).ThenByDescending(x => x.DataGb)).Take(5).ToList();

            _recommendations.Set(subscriberId, new PlanSearchState(carriers, exactDays, minDays, maxDays,
                minPrice, maxPrice, maxExclusive, forSelf, preferData,
                plans.Count > 0 ? plans.Max(x => (decimal?)x.DataGb) : prior?.LastBestDataGb));
            if (plans.Count == 0)
            {
                string constraints = exactDays.HasValue ? $" đúng {exactDays} ngày" : " theo các điều kiện đã chọn";
                string budget = maxPrice.HasValue ? $" và ngân sách không vượt {maxPrice:N0}đ" : "";
                return Done(new ChatReply($"Tôi chưa tìm thấy gói{constraints}{budget} trong dữ liệu đang bán. " +
                    "Tôi sẽ không tự đưa ra gói vượt ngân sách. Bạn muốn thay đổi thời hạn hoặc điều kiện nào?",
                    "SYSTEM", true));
            }

            string? myCarrier = await MyCarrierAsync(subscriberId, ct);
            var cards = plans.Select(x => new ChatPlan(x.Id, x.Code, x.Name, x.CarrierName,
                x.Price, x.DataGb, x.DataType, x.DurationDays,
                !forOther && !compare && myCarrier is not null && string.Equals(x.CarrierCode, myCarrier, StringComparison.OrdinalIgnoreCase))).ToArray();
            string intro = alternativeShorter && exactDays.HasValue
                ? $"Không có gói đúng {exactDays} ngày trong ngân sách/điều kiện đã chọn. Đây là {cards.Length} lựa chọn ngắn hơn, vẫn giữ giới hạn giá của bạn."
                : alternativeNearest && exactDays.HasValue
                    ? $"Không có gói đúng {exactDays} ngày. Đây là {cards.Length} gói có thời hạn gần nhất; xem số ngày thực tế trên từng thẻ."
                    : $"Tôi tìm thấy {cards.Length} gói đáp ứng các điều kiện bạn đưa ra. Bạn xem giá, dung lượng và thời hạn trên từng thẻ nhé.";
            if (forOther || compare) intro += " Đây là thông tin tư vấn; không thể đăng ký trực tiếp cho người khác bằng tài khoản của bạn.";
            else if (cards.Any(x => !x.CanRegister)) intro += " Gói khác nhà mạng của thuê bao hiện tại chỉ để tham khảo.";
            return Done(new ChatReply(intro, "SYSTEM", true,
                cards.Where(x => x.CanRegister).Select(x => new ChatOption("Đăng ký " + x.Code, "buy", x.Id)).ToArray(),
                Plans: cards));
        }

        // Cau hoi thong thuong: model phan hoi, khong duoc tu y chinh du lieu.
        var answer = await _ai.ReplyAsync(text, history, ct);
        if (string.IsNullOrWhiteSpace(answer))
            return Done(new ChatReply("Tôi chưa đủ thông tin để trả lời chính xác. Bạn có thể hỏi rõ hơn hoặc chuyển quản trị viên.",
                "SYSTEM", true, HandoffSuggested: true));
        return Done(new ChatReply(answer, "AI", true));
    }

    private static bool Has(string text, params string[] phrases) => phrases.Any(text.Contains);

    private static string ComplaintAnswer(string reason, bool brief)
    {
        if (brief)
            return "Tôi chưa đủ dữ liệu để xác định chính xác nguyên nhân. Tôi sẽ không suy đoán. " +
                "Bạn có muốn chuyển yêu cầu đến quản trị viên để kiểm tra không?";
        return reason switch
        {
            "unauthorized" => "Bạn đang báo cáo một giao dịch không do mình thực hiện. Tôi chưa thể xác minh giao dịch này hoặc tự khóa tài khoản. " +
                "Bạn có muốn chuyển yêu cầu đến quản trị viên để kiểm tra không?",
            "refund" => "Yêu cầu hoàn tiền hoặc điều chỉnh số dư cần được kiểm tra giao dịch. " +
                "Tôi không được phép tự hoàn tiền hay sửa số dư. Bạn có muốn chuyển quản trị viên không?",
            "charged" => "Bạn đang phản ánh việc bị trừ tiền hoặc gói chưa kích hoạt. " +
                "Chỉ từ tin nhắn này tôi chưa biết chính xác nguyên nhân. Bạn có thể yêu cầu xem lịch sử giao dịch, " +
                "sau đó chuyển quản trị viên nếu cần đối chiếu và xử lý khoản tiền. Bạn có muốn chuyển không?",
            "duration" => "Bạn cho biết gói bị hủy sớm hoặc hiển thị sai thời hạn. " +
                "Tôi chưa thể xác minh nguyên nhân từ nội dung tin nhắn. " +
                "Quản trị viên có thể kiểm tra thời điểm đăng ký, hủy và thời hạn của gói. Bạn có muốn chuyển yêu cầu không?",
            "failed" => "Bạn đã thử đăng ký nhưng hệ thống liên tục báo lỗi. " +
                "Tôi không khuyên thử lặp lại khi chưa biết nguyên nhân. " +
                "Bạn có muốn chuyển yêu cầu đến quản trị viên để kiểm tra không?",
            "followup" => "Bước tiếp theo, bạn có thể yêu cầu xem lịch sử giao dịch để đối chiếu. " +
                "Nếu vẫn có bất thường, hãy xác nhận chuyển quản trị viên để kiểm tra chi tiết. " +
                "Tôi chưa thể tự xác định nguyên nhân hoặc điều chỉnh tiền.",
            _ => "Tôi hiểu bạn đang gặp vấn đề với tài khoản hoặc giao dịch. " +
                "Tôi chưa thể kết luận nguyên nhân khi chưa kiểm tra dữ liệu. Bạn có muốn chuyển quản trị viên để hỗ trợ không?"
        };
    }

    public async Task<(int Status, object Body)> PrepareAsync(string action, long target, int accountId, int subscriberId, CancellationToken ct)
    {
        if (_pending.Count > 1000)
            foreach (var pair in _pending)
                if (pair.Value.Expires < DateTimeOffset.UtcNow) _pending.TryRemove(pair.Key, out _);
        string summary;
        if (action == "buy")
        {
            var plans = await _repository.GetPlansAsync(null, null, ct);
            var plan = plans.SingleOrDefault(x => x.Id == target);
            if (plan is null) return (404, new { message = "Gói cước không còn được bán." });
            summary = $"Xác nhận đăng ký gói {plan.Code} ({plan.CarrierName}), giá {plan.Price:N0}đ? Backend sẽ kiểm tra nhà mạng, số dư và gói đang sử dụng trước khi thực hiện.";
        }
        else if (action == "cancel")
        {
            var active = await _user.ActiveAsync(subscriberId, ct);
            var plan = active.SingleOrDefault(x => x.RegistrationId == target);
            if (plan is null) return (404, new { message = "Không tìm thấy gói còn hiệu lực của tài khoản." });
            summary = $"Xác nhận hủy gói {plan.Code}? Việc hủy không hoàn tiền.";
        }
        else return (400, new { message = "Thao tác không được hỗ trợ." });
        string id = Guid.NewGuid().ToString("N");
        _pending[id] = new Pending(accountId, subscriberId, action, target, DateTimeOffset.UtcNow.AddMinutes(2));
        return (200, new { confirmationId = id, message = summary });
    }

    public async Task<(int Status, object Body)> ConfirmAsync(string? id, int accountId, int subscriberId, CancellationToken ct)
    {
        // Atomic remove: moi yeu cau chi co the su dung mot lan.
        if (string.IsNullOrWhiteSpace(id) || !_pending.TryGetValue(id, out var p) ||
            p.AccountId != accountId || p.SubscriberId != subscriberId || p.Expires < DateTimeOffset.UtcNow ||
            !_pending.TryRemove(id, out p))
            return (409, new { message = "Xác nhận không hợp lệ, đã dùng hoặc hết hạn. Hãy tạo yêu cầu mới." });
        if (p.Action == "buy")
        {
            var r = await _user.BuyAsync(subscriberId, checked((int)p.TargetId), ct);
            return (r.Status, new { message = r.Message, registrationId = r.RegistrationId, balance = r.Balance });
        }
        if (p.Action == "cancel")
        {
            var r = await _user.CancelAsync(subscriberId, p.TargetId, ct);
            return (r.Status, new { message = r.Message });
        }
        return (400, new { message = "Thao tác không được hỗ trợ." });
    }
}
