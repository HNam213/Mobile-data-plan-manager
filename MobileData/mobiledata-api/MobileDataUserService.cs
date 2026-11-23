using System.Data;
using Microsoft.Data.SqlClient;

namespace MobileData.Api;

public sealed record TopupRequest(decimal Amount, string? Method = null);
public sealed record BuyPlanRequest(int PlanId);
public sealed record ActivePlanDto(long RegistrationId, int PlanId, string Code,
    string Name, decimal Price, decimal DataGb, string DataType, int DurationDays,
    DateTime StartedAt, DateTime ExpiresAt);
public sealed record HistoryDto(long Id, string Type, decimal Amount, string Status,
    string? PlanCode, string? Note, DateTime At, decimal BalanceBefore, decimal BalanceAfter);

public sealed class MobileDataUserService
{
    private readonly string _connectionString;
    public MobileDataUserService(IConfiguration configuration) =>
        _connectionString = configuration.GetConnectionString("MobileDataDB")
            ?? throw new InvalidOperationException("Thiếu chuỗi kết nối MobileDataDB.");

    private SqlConnection Connect() => new(_connectionString);

    public async Task<decimal> BalanceAsync(int subId, CancellationToken ct)
    {
        await using var db = Connect();
        await db.OpenAsync(ct);
        await using var cmd = new SqlCommand("SELECT SoDu FROM dbo.ThueBao WHERE MaThueBao=@Id", db);
        cmd.Parameters.Add("@Id", SqlDbType.Int).Value = subId;
        return Convert.ToDecimal(await cmd.ExecuteScalarAsync(ct));
    }

    // Nạp tiền CHỈ MÔ PHỎNG, không có thanh toán ngoài hoặc ngân hàng.
    public async Task<(int Status, string Message, decimal Balance)> TopupAsync(
        int subId, decimal amount, string? method, CancellationToken ct)
    {
        string kind = (method ?? "CARD").ToUpperInvariant();
        if (kind is not ("CARD" or "BANK" or "MOMO" or "ZALOPAY"))
            return (400, "Phuong thuc nap tien khong hop le.", 0);
        if (amount < 10000 || amount > 2_000_000 || decimal.Truncate(amount) != amount)
            return (400, "Số tiền nạp phải từ 10.000 đến 2.000.000đ.", 0);
        await using var db = Connect();
        await db.OpenAsync(ct);
        await using var tx = (SqlTransaction)await db.BeginTransactionAsync(IsolationLevel.Serializable, ct);
        const string sql = """
            DECLARE @Updated TABLE (NewBalance DECIMAL(18,0));
            UPDATE dbo.ThueBao
            SET SoDu = SoDu + @Amount
            OUTPUT INSERTED.SoDu INTO @Updated(NewBalance)
            WHERE MaThueBao = @Id AND TrangThai = 'ACTIVE';
            SELECT NewBalance FROM @Updated;
            """;
        await using var cmd = new SqlCommand(sql, db, tx);
        cmd.Parameters.Add("@Id", SqlDbType.Int).Value = subId;
        cmd.Parameters.Add(new SqlParameter("@Amount", SqlDbType.Decimal)
            { Precision = 18, Scale = 0, Value = amount });
        object? newBalance = await cmd.ExecuteScalarAsync(ct);
        if (newBalance is null)
        {
            await tx.RollbackAsync(ct);
            return (403, "Thuê bao không hoạt động.", 0);
        }
        await using var log = new SqlCommand("""
            INSERT dbo.GiaoDich (MaThueBao, LoaiGiaoDich, SoTien, TrangThai, GhiChu)
            VALUES (@Id, 'NAP_TIEN', @Amount, 'SUCCESS', N'Nạp tiền nội bộ: ' + @Kind);
            """, db, tx);
        log.Parameters.Add("@Id", SqlDbType.Int).Value = subId;
        log.Parameters.Add(new SqlParameter("@Amount", SqlDbType.Decimal)
            { Precision = 18, Scale = 0, Value = amount });
        log.Parameters.Add("@Kind", SqlDbType.NVarChar, 20).Value = kind;
        await log.ExecuteNonQueryAsync(ct);
        await tx.CommitAsync(ct);
        return (200, "Đã cập nhật số dư.", Convert.ToDecimal(newBalance));
    }

    public async Task<(int Status, string Message, long RegistrationId, decimal Balance)> BuyAsync(
        int subId, int planId, CancellationToken ct)
    {
        if (planId <= 0) return (400, "Gói cước không hợp lệ.", 0, 0);
        await using var db = Connect();
        await db.OpenAsync(ct);
        await using var tx = (SqlTransaction)await db.BeginTransactionAsync(IsolationLevel.Serializable, ct);

        decimal balance;
        string carrier;
        const string subscriberSql = """
            SELECT SoDu, MaNhaMang FROM dbo.ThueBao WITH (UPDLOCK, HOLDLOCK)
            WHERE MaThueBao=@Id AND TrangThai='ACTIVE';
            """;
        await using (var subscriber = new SqlCommand(subscriberSql, db, tx))
        {
            subscriber.Parameters.Add("@Id", SqlDbType.Int).Value = subId;
            await using var reader = await subscriber.ExecuteReaderAsync(ct);
            if (!await reader.ReadAsync(ct))
            {
                await reader.CloseAsync();
                await tx.RollbackAsync(ct);
                return (403, "Thuê bao đã bị khóa.", 0, 0);
            }
            balance = reader.GetDecimal(0);
            carrier = reader.GetString(1);
        }

        string code;
        decimal price, gb;
        string dataType;
        int days;
        const string planSql = """
            SELECT MaGoiHienThi, Gia, DungLuongGB, KieuDungLuong, ChuKyNgay
            FROM dbo.GoiCuoc WITH (HOLDLOCK)
            WHERE MaGoi=@PlanId AND MaNhaMang=@Carrier AND DangBan=1;
            """;
        await using (var plan = new SqlCommand(planSql, db, tx))
        {
            plan.Parameters.Add("@PlanId", SqlDbType.Int).Value = planId;
            plan.Parameters.Add("@Carrier", SqlDbType.VarChar, 5).Value = carrier;
            await using var reader = await plan.ExecuteReaderAsync(ct);
            if (!await reader.ReadAsync(ct))
            {
                await reader.CloseAsync();
                await tx.RollbackAsync(ct);
                return (404, "Gói không còn bán hoặc không thuộc nhà mạng của bạn.", 0, balance);
            }
            code = reader.GetString(0);
            price = reader.GetDecimal(1);
            gb = reader.GetDecimal(2);
            dataType = reader.GetString(3);
            days = reader.GetInt32(4);
        }

        await using (var active = new SqlCommand("""
            SELECT COUNT_BIG(*) FROM dbo.DangKyGoi WITH (UPDLOCK, HOLDLOCK)
            WHERE MaThueBao=@Id AND TrangThai='ACTIVE' AND NgayHetHan > SYSDATETIME();
            """, db, tx))
        {
            active.Parameters.Add("@Id", SqlDbType.Int).Value = subId;
            if (Convert.ToInt64(await active.ExecuteScalarAsync(ct)) > 0)
            {
                await tx.RollbackAsync(ct);
                return (409, "Bạn đang có gói còn hiệu lực. Hãy hủy hoặc đợi hết hạn trước khi đăng ký gói mới.", 0, balance);
            }
        }
        if (balance < price)
        {
            await tx.RollbackAsync(ct);
            return (409, "Số dư không đủ để đăng ký gói cước.", 0, balance);
        }

        const string registerSql = """
            DECLARE @Now DATETIME2(0) = SYSDATETIME();
            INSERT dbo.DangKyGoi
              (MaThueBao, MaGoi, GiaLucDangKy, DungLuongGBLucDangKy,
               KieuDungLuongLucDangKy, ChuKyNgayLucDangKy, NgayDangKy, NgayHetHan)
            OUTPUT INSERTED.MaDangKy
            VALUES (@SubId, @PlanId, @Price, @DataGb, @DataType, @Days,
                    @Now, DATEADD(DAY, @Days, @Now));
            """;
        long registrationId;
        await using (var register = new SqlCommand(registerSql, db, tx))
        {
            register.Parameters.Add("@SubId", SqlDbType.Int).Value = subId;
            register.Parameters.Add("@PlanId", SqlDbType.Int).Value = planId;
            register.Parameters.Add(new SqlParameter("@Price", SqlDbType.Decimal)
                { Precision = 18, Scale = 0, Value = price });
            register.Parameters.Add(new SqlParameter("@DataGb", SqlDbType.Decimal)
                { Precision = 10, Scale = 2, Value = gb });
            register.Parameters.Add("@DataType", SqlDbType.VarChar, 10).Value = dataType;
            register.Parameters.Add("@Days", SqlDbType.Int).Value = days;
            registrationId = Convert.ToInt64(await register.ExecuteScalarAsync(ct));
        }
        await using (var deduct = new SqlCommand("""
            UPDATE dbo.ThueBao SET SoDu=SoDu-@Price
            WHERE MaThueBao=@Id AND SoDu>=@Price;
            """, db, tx))
        {
            deduct.Parameters.Add("@Id", SqlDbType.Int).Value = subId;
            deduct.Parameters.Add(new SqlParameter("@Price", SqlDbType.Decimal)
                { Precision = 18, Scale = 0, Value = price });
            if (await deduct.ExecuteNonQueryAsync(ct) != 1)
            {
                await tx.RollbackAsync(ct);
                return (409, "Số dư không đủ.", 0, balance);
            }
        }
        await using (var log = new SqlCommand("""
            INSERT dbo.GiaoDich (MaThueBao, MaDangKy, LoaiGiaoDich,
                SoTien, TrangThai, GhiChu)
            VALUES (@SubId,@RegId,'DANG_KY_GOI',@Price,'SUCCESS',@Note);
            """, db, tx))
        {
            log.Parameters.Add("@SubId", SqlDbType.Int).Value = subId;
            log.Parameters.Add("@RegId", SqlDbType.BigInt).Value = registrationId;
            log.Parameters.Add(new SqlParameter("@Price", SqlDbType.Decimal)
                { Precision = 18, Scale = 0, Value = price });
            log.Parameters.Add("@Note", SqlDbType.NVarChar, 300).Value = "Đăng ký " + code;
            await log.ExecuteNonQueryAsync(ct);
        }
        await tx.CommitAsync(ct);
        return (200, "Đăng ký gói thành công (mô phỏng).", registrationId, balance - price);
    }

    public async Task<IReadOnlyList<ActivePlanDto>> ActiveAsync(int subId, CancellationToken ct)
    {
        const string sql = """
            SELECT dk.MaDangKy, gc.MaGoi, gc.MaGoiHienThi, gc.TenGoi,
                   dk.GiaLucDangKy, dk.DungLuongGBLucDangKy,
                   dk.KieuDungLuongLucDangKy, dk.ChuKyNgayLucDangKy,
                   dk.NgayDangKy, dk.NgayHetHan
            FROM dbo.DangKyGoi AS dk
            JOIN dbo.GoiCuoc AS gc ON gc.MaGoi=dk.MaGoi
            WHERE dk.MaThueBao=@Id AND dk.TrangThai='ACTIVE'
              AND dk.NgayHuy IS NULL AND dk.NgayHetHan>SYSDATETIME()
            ORDER BY dk.NgayDangKy DESC;
            """;
        var result = new List<ActivePlanDto>();
        await using var db = Connect();
        await db.OpenAsync(ct);
        await using var cmd = new SqlCommand(sql, db);
        cmd.Parameters.Add("@Id", SqlDbType.Int).Value = subId;
        await using var reader = await cmd.ExecuteReaderAsync(ct);
        while (await reader.ReadAsync(ct))
            result.Add(new ActivePlanDto(reader.GetInt64(0), reader.GetInt32(1),
                reader.GetString(2), reader.GetString(3), reader.GetDecimal(4),
                reader.GetDecimal(5), reader.GetString(6), reader.GetInt32(7),
                reader.GetDateTime(8), reader.GetDateTime(9)));
        return result;
    }

    public async Task<(int Status, string Message)> CancelAsync(int subId, long regId, CancellationToken ct)
    {
        if (regId <= 0) return (400, "Mã lượt đăng ký không hợp lệ.");
        await using var db = Connect();
        await db.OpenAsync(ct);
        await using var tx = (SqlTransaction)await db.BeginTransactionAsync(IsolationLevel.Serializable, ct);
        const string cancelSql = """
            UPDATE dbo.DangKyGoi
            SET TrangThai='CANCELED', NgayHuy=SYSDATETIME()
            WHERE MaDangKy=@RegId AND MaThueBao=@SubId
              AND TrangThai='ACTIVE' AND NgayHetHan>SYSDATETIME();
            """;
        await using (var cmd = new SqlCommand(cancelSql, db, tx))
        {
            cmd.Parameters.Add("@RegId", SqlDbType.BigInt).Value = regId;
            cmd.Parameters.Add("@SubId", SqlDbType.Int).Value = subId;
            if (await cmd.ExecuteNonQueryAsync(ct) != 1)
            {
                await tx.RollbackAsync(ct);
                return (409, "Gói đã hủy, hết hạn hoặc không thuộc tài khoản của bạn.");
            }
        }
        await using (var log = new SqlCommand("""
            INSERT dbo.GiaoDich (MaThueBao, MaDangKy, LoaiGiaoDich,
                SoTien, TrangThai, GhiChu)
            VALUES (@SubId,@RegId,'HUY_GOI',0,'SUCCESS',N'Hủy gói trước hạn (không hoàn tiền)');
            """, db, tx))
        {
            log.Parameters.Add("@RegId", SqlDbType.BigInt).Value = regId;
            log.Parameters.Add("@SubId", SqlDbType.Int).Value = subId;
            await log.ExecuteNonQueryAsync(ct);
        }
        await tx.CommitAsync(ct);
        return (200, "Đã hủy gói. Không hoàn tiền và không tự gia hạn.");
    }

    // Tính số dư trước/sau dựa trên sổ giao dịch và số dư hiện tại. Áp dụng
    // được cho cả các giao dịch đã tồn tại từ trước khi cập nhật API.
    public async Task<IReadOnlyList<HistoryDto>> HistoryAsync(int subId, CancellationToken ct)
    {
        const string sql = """
            WITH Events AS (
                SELECT gd.MaGiaoDich, gd.LoaiGiaoDich, gd.SoTien,
                       gd.TrangThai, gc.MaGoiHienThi, gd.GhiChu, gd.NgayGiaoDich,
                       tb.SoDu AS CurrentBalance,
                       CAST(CASE
                         WHEN gd.TrangThai <> 'SUCCESS' THEN 0
                         WHEN gd.LoaiGiaoDich = 'NAP_TIEN' THEN gd.SoTien
                         WHEN gd.LoaiGiaoDich = 'DANG_KY_GOI' THEN -gd.SoTien
                         ELSE 0 END AS DECIMAL(18,0)) AS Delta
                FROM dbo.GiaoDich gd
                JOIN dbo.ThueBao tb ON tb.MaThueBao = gd.MaThueBao
                LEFT JOIN dbo.DangKyGoi dk ON dk.MaDangKy = gd.MaDangKy
                LEFT JOIN dbo.GoiCuoc gc ON gc.MaGoi = dk.MaGoi
                WHERE gd.MaThueBao=@Id
            ), WithBalance AS (
                SELECT *, CAST(CurrentBalance - COALESCE(
                  SUM(Delta) OVER (ORDER BY NgayGiaoDich DESC, MaGiaoDich DESC
                      ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING), 0)
                  AS DECIMAL(18,0)) AS BalanceAfter
                FROM Events
            )
            SELECT TOP (200) MaGiaoDich, LoaiGiaoDich, SoTien, TrangThai,
                MaGoiHienThi, GhiChu, NgayGiaoDich, BalanceAfter,
                CAST(BalanceAfter - Delta AS DECIMAL(18,0)) AS BalanceBefore
            FROM WithBalance ORDER BY NgayGiaoDich DESC, MaGiaoDich DESC;
            """;
        var result = new List<HistoryDto>();
        await using var db = Connect();
        await db.OpenAsync(ct);
        await using var cmd = new SqlCommand(sql, db);
        cmd.Parameters.Add("@Id", SqlDbType.Int).Value = subId;
        await using var reader = await cmd.ExecuteReaderAsync(ct);
        while (await reader.ReadAsync(ct))
            result.Add(new HistoryDto(reader.GetInt64(0), reader.GetString(1),
                reader.GetDecimal(2), reader.GetString(3),
                reader.IsDBNull(4) ? null : reader.GetString(4),
                reader.IsDBNull(5) ? null : reader.GetString(5), reader.GetDateTime(6),
                reader.GetDecimal(8), reader.GetDecimal(7)));
        return result;
    }
}