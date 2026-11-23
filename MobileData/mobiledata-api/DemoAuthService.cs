using System.Collections.Concurrent;
using System.Data;
using System.Security.Cryptography;
using System.Text;
using Microsoft.Data.SqlClient;

namespace MobileData.Api;

// Các DTO nhận từ HTTP. Không có trường VaiTro từ client.
public sealed record OtpStartRequest(string? Phone, string? CarrierCode);
public sealed record OtpVerifyRequest(long RequestId, string? Otp);
public sealed record ApiAuthResult(int StatusCode, object Body);

public static class DemoAuthEndpoints
{
    public static void MapDemoAuth(this WebApplication app)
    {
        app.MapPost("/api/auth/request-otp", async (OtpStartRequest request,
            DemoAuthService service, CancellationToken ct) =>
        {
            var result = await service.RequestOtpAsync(request, ct);
            return Results.Json(result.Body, statusCode: result.StatusCode);
        });

        app.MapPost("/api/auth/verify-otp", async (OtpVerifyRequest request,
            DemoAuthService service, CancellationToken ct) =>
        {
            var result = await service.VerifyOtpAsync(request, ct);
            return Results.Json(result.Body, statusCode: result.StatusCode);
        });

        app.MapGet("/api/auth/me", async (HttpRequest request,
            DemoAuthService service, CancellationToken ct) =>
        {
            var result = await service.MeAsync(request.Headers.Authorization.ToString(), ct);
            return Results.Json(result.Body, statusCode: result.StatusCode);
        });

        app.MapPost("/api/auth/logout", (HttpRequest request, DemoAuthService service) =>
        {
            var result = service.Logout(request.Headers.Authorization.ToString());
            return Results.Json(result.Body, statusCode: result.StatusCode);
        });
    }
}

/// <summary>
/// OTP và phiên đăng nhập chỉ để DEMO CỤC BỘ.
/// OTP HMAC lưu SQL Server; token phiên chỉ tồn tại trong RAM Web API.
/// Khởi động lại Web API sẽ làm các OTP đang chờ và phiên đăng nhập hết hiệu lực.
/// Chưa thay thế được hệ thống xác thực thật qua SMS/HTTPS/Identity.
/// </summary>
public sealed class DemoAuthService
{
    private readonly string _connectionString;
    private readonly bool _isDevelopment;
    private readonly byte[] _otpKey = RandomNumberGenerator.GetBytes(32);
    private readonly ConcurrentDictionary<string, LoginSession> _sessions = new();
    // Một tài khoản chỉ có một token còn hiệu lực tại một thời điểm.
    // Khóa đảm bảo hai yêu cầu OTP đến đồng thời không thể mở hai phiên.
    private readonly ConcurrentDictionary<int, string> _activeTokenByAccount = new();
    private readonly object _sessionGate = new();
    private const string SessionAlreadyActiveMessage =
        "Tài khoản đang được đăng nhập trên thiết bị khác. Vui lòng đăng xuất ở thiết bị đó trước khi đăng nhập tại đây.";

    private sealed record LoginSession(int AccountId, DateTime ExpiresUtc);

    private static ApiAuthResult SessionAlreadyActive() =>
        new(409, new { code = "ACCOUNT_ALREADY_ACTIVE", message = SessionAlreadyActiveMessage });

    // Chỉ gọi khi đã giữ _sessionGate.
    private bool HasActiveSessionLocked(int accountId)
    {
        if (!_activeTokenByAccount.TryGetValue(accountId, out string? hash))
            return false;
        if (_sessions.TryGetValue(hash, out var session) && session.ExpiresUtc > DateTime.UtcNow)
            return true;
        _activeTokenByAccount.TryRemove(accountId, out _);
        _sessions.TryRemove(hash, out _);
        return false;
    }

    private bool HasActiveSession(int accountId)
    {
        lock (_sessionGate) return HasActiveSessionLocked(accountId);
    }

    public DemoAuthService(IConfiguration configuration, IHostEnvironment environment)
    {
        _connectionString = configuration.GetConnectionString("MobileDataDB")
            ?? throw new InvalidOperationException("Thiếu ConnectionStrings:MobileDataDB.");
        _isDevelopment = environment.IsDevelopment();
    }

    private static ApiAuthResult Fail(int status, string error) =>
        new(status, new { message = error });

    private SqlConnection NewConnection() => new(_connectionString);

    private byte[] HashOtp(int accountId, string code)
    {
        using var hmac = new HMACSHA256(_otpKey);
        return hmac.ComputeHash(Encoding.UTF8.GetBytes($"MobileData:{accountId}:{code}"));
    }

    private static bool ValidPhone(string? phone) =>
        phone is { Length: 10 } && phone[0] == '0' && "35789".Contains(phone[1]) && phone.All(char.IsAsciiDigit);

    public async Task<ApiAuthResult> RequestOtpAsync(OtpStartRequest request, CancellationToken ct)
    {
        string phone = (request.Phone ?? "").Trim();
        string carrier = (request.CarrierCode ?? "").Trim().ToUpperInvariant();
        if (!ValidPhone(phone) || carrier is not ("VN" or "VT" or "MB"))
            return Fail(400, "Số điện thoại hoặc nhà mạng không hợp lệ.");

        await using var db = NewConnection();
        await db.OpenAsync(ct);
        await using var tx = (SqlTransaction)await db.BeginTransactionAsync(IsolationLevel.Serializable, ct);
        const string findSql = """
            SELECT TOP(1) tk.MaTaiKhoan
            FROM dbo.TaiKhoan AS tk WITH (UPDLOCK, HOLDLOCK)
            INNER JOIN dbo.ThueBao AS tb ON tb.MaThueBao = tk.MaThueBao
            INNER JOIN dbo.NhaMang AS nm ON nm.MaNhaMang = tb.MaNhaMang
            WHERE tb.SoDienThoai = @Phone AND tb.MaNhaMang = @Carrier
              AND tk.DangHoatDong = 1 AND tb.TrangThai = 'ACTIVE'
              AND nm.DangHoatDong = 1;
            """;
        await using var find = new SqlCommand(findSql, db, tx);
        find.Parameters.Add("@Phone", SqlDbType.VarChar, 10).Value = phone;
        find.Parameters.Add("@Carrier", SqlDbType.VarChar, 5).Value = carrier;
        var existing = await find.ExecuteScalarAsync(ct);
        if (existing is null)
        {
            await tx.RollbackAsync(ct);
            return Fail(404, "Không tìm thấy thuê bao đang hoạt động tương ứng.");
        }
        int accountId = (int)existing;
        // Chặn yêu cầu OTP ngay khi tài khoản đã có phiên đang hoạt động.
        if (HasActiveSession(accountId))
        {
            await tx.RollbackAsync(ct);
            return SessionAlreadyActive();
        }

        const string cooldownSql = """
            SELECT CASE WHEN MAX(NgayTao) IS NULL THEN 9999
                        ELSE DATEDIFF(SECOND, MAX(NgayTao), SYSDATETIME()) END
            FROM dbo.YeuCauOtp WHERE MaTaiKhoan = @AccountId;
            """;
        await using var cooldown = new SqlCommand(cooldownSql, db, tx);
        cooldown.Parameters.Add("@AccountId", SqlDbType.Int).Value = accountId;
        int seconds = Convert.ToInt32(await cooldown.ExecuteScalarAsync(ct));
        if (seconds < 30)
        {
            await tx.RollbackAsync(ct);
            return Fail(429, $"Vui lòng thử lại sau {30 - Math.Max(0, seconds)} giây.");
        }

        string otp = RandomNumberGenerator.GetInt32(0, 1_000_000).ToString("D6");
        const string addSql = """
            INSERT dbo.YeuCauOtp (MaTaiKhoan, MaOtpHash, HetHan)
            OUTPUT INSERTED.MaYeuCauOtp
            VALUES (@AccountId, @OtpHash, DATEADD(MINUTE, 2, SYSDATETIME()));
            """;
        await using var add = new SqlCommand(addSql, db, tx);
        add.Parameters.Add("@AccountId", SqlDbType.Int).Value = accountId;
        add.Parameters.Add("@OtpHash", SqlDbType.VarBinary, 32).Value = HashOtp(accountId, otp);
        long requestId = Convert.ToInt64(await add.ExecuteScalarAsync(ct));
        await tx.CommitAsync(ct);

        // Chỉ để thử nghiệm trên máy phát triển. KHÔNG trả OTP ở Production.
        object body = _isDevelopment
            ? new { requestId, expiresInSeconds = 120, devOtp = otp,
                note = "OTP mô phỏng. Không dùng API này để xác thực thực tế." }
            : new { requestId, expiresInSeconds = 120,
                note = "Cần tích hợp SMS OTP thật trước khi triển khai Production." };
        return new ApiAuthResult(200, body);
    }

    public async Task<ApiAuthResult> VerifyOtpAsync(OtpVerifyRequest request, CancellationToken ct)
    {
        string code = (request.Otp ?? "").Trim();
        if (request.RequestId <= 0 || code.Length != 6 || !code.All(char.IsAsciiDigit))
            return Fail(400, "Mã yêu cầu hoặc OTP không hợp lệ.");

        await using var db = NewConnection();
        await db.OpenAsync(ct);
        await using var tx = (SqlTransaction)await db.BeginTransactionAsync(IsolationLevel.ReadCommitted, ct);
        const string sql = """
            SELECT otp.MaTaiKhoan, otp.MaOtpHash, otp.SoLanNhapSai, otp.DaSuDung,
                   CASE WHEN otp.HetHan <= SYSDATETIME() THEN CAST(1 AS BIT)
                        ELSE CAST(0 AS BIT) END AS DaHetHan,
                   tk.DangHoatDong, tb.TrangThai
            FROM dbo.YeuCauOtp AS otp WITH (UPDLOCK, ROWLOCK)
            INNER JOIN dbo.TaiKhoan AS tk ON tk.MaTaiKhoan = otp.MaTaiKhoan
            INNER JOIN dbo.ThueBao AS tb ON tb.MaThueBao = tk.MaThueBao
            WHERE otp.MaYeuCauOtp = @RequestId;
            """;
        await using var cmd = new SqlCommand(sql, db, tx);
        cmd.Parameters.Add("@RequestId", SqlDbType.BigInt).Value = request.RequestId;

        int accountId;
        byte[] savedHash;
        int fails;
        bool used, expired, active;
        await using (var reader = await cmd.ExecuteReaderAsync(ct))
        {
            if (!await reader.ReadAsync(ct))
            {
                return Fail(400, "Yêu cầu OTP không tồn tại.");
            }
            accountId = reader.GetInt32(0);
            savedHash = (byte[])reader.GetValue(1);
            fails = reader.GetInt32(2);
            used = reader.GetBoolean(3);
            expired = reader.GetBoolean(4);
            active = reader.GetBoolean(5) && reader.GetString(6) == "ACTIVE";
        }

        if (used || expired || fails >= 5 || !active)
        {
            await tx.RollbackAsync(ct);
            return Fail(401, "OTP đã hết hạn, bị khóa hoặc không còn hiệu lực.");
        }

        if (!CryptographicOperations.FixedTimeEquals(savedHash, HashOtp(accountId, code)))
        {
            await using var update = new SqlCommand(
                "UPDATE dbo.YeuCauOtp SET SoLanNhapSai = SoLanNhapSai + 1 WHERE MaYeuCauOtp = @Id", db, tx);
            update.Parameters.Add("@Id", SqlDbType.BigInt).Value = request.RequestId;
            await update.ExecuteNonQueryAsync(ct);
            await tx.CommitAsync(ct);
            return Fail(401, "OTP không đúng.");
        }

        await using (var update = new SqlCommand(
            "UPDATE dbo.YeuCauOtp SET DaSuDung = 1 WHERE MaYeuCauOtp = @Id", db, tx))
        {
            update.Parameters.Add("@Id", SqlDbType.BigInt).Value = request.RequestId;
            await update.ExecuteNonQueryAsync(ct);
        }
        await tx.CommitAsync(ct);

        var profile = await ReadProfileAsync(accountId, ct);
        if (profile is null)
            return Fail(401, "Tài khoản đã bị khóa hoặc không tồn tại.");

        string token = Convert.ToHexString(RandomNumberGenerator.GetBytes(32));
        var expiresAtUtc = DateTime.UtcNow.AddHours(1);
        string tokenHash = HashToken(token);
        lock (_sessionGate)
        {
            // Kiểm tra lần hai: hai máy có thể đã lấy OTP trước khi máy nào đăng nhập.
            // Khóa làm thao tác kiểm tra và ghi token thành một bước nguyên tử.
            if (HasActiveSessionLocked(accountId))
                return SessionAlreadyActive();
            _sessions[tokenHash] = new LoginSession(accountId, expiresAtUtc);
            _activeTokenByAccount[accountId] = tokenHash;
        }
        return new ApiAuthResult(200, new
        {
            accessToken = token,
            tokenType = "Bearer",
            expiresAtUtc,
            profile
        });
    }

    private static string HashToken(string token) =>
        Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(token)));

    private int? GetAccountId(string authorization)
    {
        if (!authorization.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase))
            return null;
        string token = authorization[7..].Trim();
        if (token.Length != 64 || !token.All(Uri.IsHexDigit))
            return null;
        string hash = HashToken(token);
        lock (_sessionGate)
        {
            if (!_sessions.TryGetValue(hash, out var session))
                return null;
            if (session.ExpiresUtc <= DateTime.UtcNow)
            {
                _sessions.TryRemove(hash, out _);
                if (_activeTokenByAccount.TryGetValue(session.AccountId, out string? current)
                    && current == hash)
                    _activeTokenByAccount.TryRemove(session.AccountId, out _);
                return null;
            }
            // Một token cũ không được dùng nếu tài khoản đã được mở phiên khác.
            if (!_activeTokenByAccount.TryGetValue(session.AccountId, out string? activeHash)
                || activeHash != hash)
                return null;
            return session.AccountId;
        }
    }

    public async Task<ApiAuthResult> MeAsync(string authHeader, CancellationToken ct)
    {
        int? accountId = GetAccountId(authHeader);
        if (accountId is null)
            return Fail(401, "Phiên đăng nhập không hợp lệ hoặc đã hết hạn.");
        var profile = await ReadProfileAsync(accountId.Value, ct);
        return profile is null
            ? Fail(401, "Tài khoản đã bị khóa hoặc không còn tồn tại.")
            : new ApiAuthResult(200, profile);
    }

    // Mỗi yêu cầu nhạy cảm xác thực lại token và trạng thái tài khoản tại SQL Server.
    public sealed record ActiveIdentity(int AccountId, int SubscriberId, string Role);

    public async Task<ActiveIdentity?> GetActiveIdentityAsync(string authorization, CancellationToken ct)
    {
        int? accountId = GetAccountId(authorization);
        if (accountId is null) return null;
        const string sql = """
            SELECT tk.MaTaiKhoan, tb.MaThueBao, tk.VaiTro
            FROM dbo.TaiKhoan AS tk
            JOIN dbo.ThueBao AS tb ON tb.MaThueBao = tk.MaThueBao
            JOIN dbo.NhaMang AS nm ON nm.MaNhaMang = tb.MaNhaMang
            WHERE tk.MaTaiKhoan = @Id AND tk.DangHoatDong = 1
              AND tb.TrangThai = 'ACTIVE' AND nm.DangHoatDong = 1;
            """;
        await using var db = NewConnection();
        await db.OpenAsync(ct);
        await using var command = new SqlCommand(sql, db);
        command.Parameters.Add("@Id", SqlDbType.Int).Value = accountId.Value;
        await using var reader = await command.ExecuteReaderAsync(ct);
        return await reader.ReadAsync(ct)
            ? new ActiveIdentity(reader.GetInt32(0), reader.GetInt32(1), reader.GetString(2))
            : null;
    }

    // Quyền ADMIN được kiểm tra lại ở SQL Server cho MỖI yêu cầu quản trị.
    // Không sử dụng role do ứng dụng Android tự gửi lên.
    public async Task<int> CheckAdminAccessAsync(string authHeader, CancellationToken ct)
    {
        int? accountId = GetAccountId(authHeader);
        if (accountId is null) return 401;

        const string sql = """
            SELECT tk.VaiTro
            FROM dbo.TaiKhoan AS tk
            INNER JOIN dbo.ThueBao AS tb ON tb.MaThueBao = tk.MaThueBao
            INNER JOIN dbo.NhaMang AS nm ON nm.MaNhaMang = tb.MaNhaMang
            WHERE tk.MaTaiKhoan = @Id
              AND tk.DangHoatDong = 1
              AND tb.TrangThai = 'ACTIVE'
              AND nm.DangHoatDong = 1;
            """;
        await using var db = NewConnection();
        await db.OpenAsync(ct);
        await using var cmd = new SqlCommand(sql, db);
        cmd.Parameters.Add("@Id", SqlDbType.Int).Value = accountId.Value;
        object? role = await cmd.ExecuteScalarAsync(ct);
        if (role is not string roleText) return 401;
        return roleText == "ADMIN" ? 200 : 403;
    }

    public ApiAuthResult Logout(string authHeader)
    {
        if (authHeader.StartsWith("Bearer ", StringComparison.OrdinalIgnoreCase))
        {
            string token = authHeader[7..].Trim();
            if (token.Length == 64 && token.All(Uri.IsHexDigit))
            {
                string hash = HashToken(token);
                lock (_sessionGate)
                {
                    if (_sessions.TryRemove(hash, out var session)
                        && _activeTokenByAccount.TryGetValue(session.AccountId, out string? activeHash)
                        && activeHash == hash)
                        _activeTokenByAccount.TryRemove(session.AccountId, out _);
                }
            }
        }
        return new ApiAuthResult(200, new { message = "Đã đăng xuất khỏi phiên API." });
    }

    private async Task<object?> ReadProfileAsync(int accountId, CancellationToken ct)
    {
        const string sql = """
            SELECT kh.HoTen, tb.SoDienThoai, tb.MaNhaMang,
                   nm.TenNhaMang, tb.SoDu, tk.VaiTro
            FROM dbo.TaiKhoan AS tk
            JOIN dbo.ThueBao AS tb ON tb.MaThueBao = tk.MaThueBao
            JOIN dbo.KhachHang AS kh ON kh.MaKhachHang = tb.MaKhachHang
            JOIN dbo.NhaMang AS nm ON nm.MaNhaMang = tb.MaNhaMang
            WHERE tk.MaTaiKhoan = @Id AND tk.DangHoatDong = 1
              AND tb.TrangThai = 'ACTIVE' AND nm.DangHoatDong = 1;
            """;
        await using var db = NewConnection();
        await db.OpenAsync(ct);
        await using var cmd = new SqlCommand(sql, db);
        cmd.Parameters.Add("@Id", SqlDbType.Int).Value = accountId;
        await using var reader = await cmd.ExecuteReaderAsync(ct);
        if (!await reader.ReadAsync(ct)) return null;
        return new
        {
            fullName = reader.GetString(0),
            phone = reader.GetString(1),
            carrierCode = reader.GetString(2),
            carrierName = reader.GetString(3),
            balance = reader.GetDecimal(4),
            role = reader.GetString(5)
        };
    }
}
