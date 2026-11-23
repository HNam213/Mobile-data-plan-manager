using System.Data;
using Microsoft.Data.SqlClient;

namespace MobileData.Api;

public sealed record SupportCreateRequest(string? Title, string? Message);
public sealed record SupportReplyRequest(string? Message);
public sealed record SupportStatusRequest(string? Status);
public sealed record SupportTicketDto(long Id, string Title, string Status, string CarrierName,
    string Phone, string FullName, DateTime CreatedAt, DateTime UpdatedAt, int MessageCount);
public sealed record SupportMessageDto(long Id, string SenderRole, string Message, DateTime SentAt);
public sealed record SupportDetailDto(SupportTicketDto Ticket, IReadOnlyList<SupportMessageDto> Messages);

public sealed record SupportReplyPreviewDto(long Id, long TicketId, string Title, string Message, DateTime SentAt);
public sealed record SupportReplyFeedDto(long LatestId, IReadOnlyList<SupportReplyPreviewDto> Items);

/// <summary>Hộp thư hỗ trợ mô phỏng. Chỉ truy cập qua API đã xác thực.</summary>
public sealed class SupportService
{
    private readonly string _connectionString;
    public SupportService(IConfiguration config) => _connectionString =
        config.GetConnectionString("MobileDataDB") ?? throw new InvalidOperationException("Thiếu kết nối MobileDataDB.");
    private SqlConnection Connect() => new(_connectionString);

    // Khởi tạo hai bảng hỗ trợ bổ sung, không xóa/sửa bảng dữ liệu hiện có.
    public async Task EnsureTablesAsync(CancellationToken ct)
    {
        const string sql = """
            IF OBJECT_ID(N'dbo.YeuCauHoTro', N'U') IS NULL
            BEGIN
                CREATE TABLE dbo.YeuCauHoTro (
                    MaYeuCau BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
                    MaThueBao INT NOT NULL REFERENCES dbo.ThueBao(MaThueBao),
                    TieuDe NVARCHAR(160) NOT NULL,
                    TrangThai VARCHAR(12) NOT NULL DEFAULT 'OPEN'
                       CHECK (TrangThai IN ('OPEN','ANSWERED','CLOSED')),
                    NgayTao DATETIME2(0) NOT NULL DEFAULT SYSDATETIME(),
                    NgayCapNhat DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()
                );
            END;
            IF OBJECT_ID(N'dbo.TinNhanHoTro', N'U') IS NULL
            BEGIN
                CREATE TABLE dbo.TinNhanHoTro (
                    MaTinNhan BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
                    MaYeuCau BIGINT NOT NULL REFERENCES dbo.YeuCauHoTro(MaYeuCau),
                    MaTaiKhoan INT NOT NULL REFERENCES dbo.TaiKhoan(MaTaiKhoan),
                    VaiTroNguoiGui VARCHAR(5) NOT NULL
                        CHECK (VaiTroNguoiGui IN ('USER','ADMIN')),
                    NoiDung NVARCHAR(2000) NOT NULL,
                    NgayGui DATETIME2(0) NOT NULL DEFAULT SYSDATETIME()
                );
            END;
            IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'IX_HoTro_ThueBao' AND object_id=OBJECT_ID(N'dbo.YeuCauHoTro'))
                EXEC(N'CREATE INDEX IX_HoTro_ThueBao ON dbo.YeuCauHoTro(MaThueBao, NgayCapNhat DESC)');
            IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name=N'IX_HoTro_TinNhan' AND object_id=OBJECT_ID(N'dbo.TinNhanHoTro'))
                EXEC(N'CREATE INDEX IX_HoTro_TinNhan ON dbo.TinNhanHoTro(MaYeuCau, MaTinNhan)');
            """;
        await using var db = Connect();
        await db.OpenAsync(ct);
        await using var cmd = new SqlCommand(sql, db) { CommandTimeout = 45 };
        await cmd.ExecuteNonQueryAsync(ct);
    }

    // SQL duoc noi them WHERE phai co ky tu xuong dong/space de khong dinh vao MaKhachHang.
    private const string TicketQuery = """
        SELECT y.MaYeuCau, y.TieuDe, y.TrangThai, nm.TenNhaMang,
               tb.SoDienThoai, kh.HoTen, y.NgayTao, y.NgayCapNhat,
               (SELECT COUNT(*) FROM dbo.TinNhanHoTro m WHERE m.MaYeuCau = y.MaYeuCau)
        FROM dbo.YeuCauHoTro y
        JOIN dbo.ThueBao tb ON tb.MaThueBao = y.MaThueBao
        JOIN dbo.NhaMang nm ON nm.MaNhaMang = tb.MaNhaMang
        JOIN dbo.KhachHang kh ON kh.MaKhachHang = tb.MaKhachHang
        """;
    private static SupportTicketDto Ticket(SqlDataReader r) => new(
        r.GetInt64(0), r.GetString(1), r.GetString(2), r.GetString(3),
        r.GetString(4), r.GetString(5), r.GetDateTime(6), r.GetDateTime(7), r.GetInt32(8));

    public async Task<IReadOnlyList<SupportTicketDto>> ListAsync(int? subId, CancellationToken ct)
    {
        const string where = "WHERE (@SubId IS NULL OR y.MaThueBao = @SubId) ORDER BY y.NgayCapNhat DESC, y.MaYeuCau DESC";
        var tickets = new List<SupportTicketDto>();
        await using var db = Connect();
        await db.OpenAsync(ct);
        await using var cmd = new SqlCommand(TicketQuery + "\n" + where, db);
        cmd.Parameters.Add("@SubId", SqlDbType.Int).Value = (object?)subId ?? DBNull.Value;
        await using var r = await cmd.ExecuteReaderAsync(ct);
        while (await r.ReadAsync(ct)) tickets.Add(Ticket(r));
        return tickets;
    }

    public async Task<SupportDetailDto?> DetailAsync(long id, int? subId, CancellationToken ct)
    {
        await using var db = Connect();
        await db.OpenAsync(ct);
        SupportTicketDto? ticket;
        await using (var cmd = new SqlCommand(TicketQuery + "\n" +
            "WHERE y.MaYeuCau=@Id AND (@SubId IS NULL OR y.MaThueBao=@SubId)", db))
        {
            cmd.Parameters.Add("@Id", SqlDbType.BigInt).Value = id;
            cmd.Parameters.Add("@SubId", SqlDbType.Int).Value = (object?)subId ?? DBNull.Value;
            await using var reader = await cmd.ExecuteReaderAsync(ct);
            ticket = await reader.ReadAsync(ct) ? Ticket(reader) : null;
        }
        if (ticket is null) return null;
        var messages = new List<SupportMessageDto>();
        await using (var cmd = new SqlCommand("""
            SELECT MaTinNhan, VaiTroNguoiGui, NoiDung, NgayGui
            FROM dbo.TinNhanHoTro WHERE MaYeuCau=@Id ORDER BY MaTinNhan ASC;
            """, db))
        {
            cmd.Parameters.Add("@Id", SqlDbType.BigInt).Value = id;
            await using var reader = await cmd.ExecuteReaderAsync(ct);
            while (await reader.ReadAsync(ct))
                messages.Add(new SupportMessageDto(reader.GetInt64(0), reader.GetString(1),
                    reader.GetString(2), reader.GetDateTime(3)));
        }
        return new SupportDetailDto(ticket, messages);
    }

    // Chi tra phan hoi ADMIN thuoc thue bao dang dang nhap, su dung ID tang dan.
    // API trả mốc mới nhất kể cả khi chưa có thông báo mới để ứng dụng đặt mốc ban đầu.
    public async Task<SupportReplyFeedDto> NewRepliesAsync(int subscriberId, long afterId, CancellationToken ct)
    {
        await using var db = Connect();
        await db.OpenAsync(ct);
        long latestId;
        await using (var max = new SqlCommand("""
            SELECT COALESCE(MAX(m.MaTinNhan), CONVERT(BIGINT, 0))
            FROM dbo.TinNhanHoTro AS m
            INNER JOIN dbo.YeuCauHoTro AS t ON t.MaYeuCau = m.MaYeuCau
            WHERE t.MaThueBao = @SubId AND m.VaiTroNguoiGui = 'ADMIN';
            """, db))
        {
            max.Parameters.Add("@SubId", SqlDbType.Int).Value = subscriberId;
            latestId = Convert.ToInt64(await max.ExecuteScalarAsync(ct));
        }
        var messages = new List<SupportReplyPreviewDto>();
        await using (var cmd = new SqlCommand("""
            SELECT TOP (30) m.MaTinNhan, m.MaYeuCau, t.TieuDe, m.NoiDung, m.NgayGui
            FROM dbo.TinNhanHoTro AS m
            INNER JOIN dbo.YeuCauHoTro AS t ON t.MaYeuCau = m.MaYeuCau
            WHERE t.MaThueBao = @SubId AND m.VaiTroNguoiGui = 'ADMIN'
              AND m.MaTinNhan > @After
            ORDER BY m.MaTinNhan ASC;
            """, db))
        {
            cmd.Parameters.Add("@SubId", SqlDbType.Int).Value = subscriberId;
            cmd.Parameters.Add("@After", SqlDbType.BigInt).Value = Math.Max(0L, afterId);
            await using var reader = await cmd.ExecuteReaderAsync(ct);
            while (await reader.ReadAsync(ct))
                messages.Add(new SupportReplyPreviewDto(reader.GetInt64(0), reader.GetInt64(1),
                    reader.GetString(2), reader.GetString(3), reader.GetDateTime(4)));
        }
        return new SupportReplyFeedDto(latestId, messages);
    }

    public async Task<int?> GetTicketOwnerAsync(long ticketId, CancellationToken ct)
    {
        await using var db = Connect();
        await db.OpenAsync(ct);
        await using var cmd = new SqlCommand("SELECT MaThueBao FROM dbo.YeuCauHoTro WHERE MaYeuCau=@Id", db);
        cmd.Parameters.Add("@Id", SqlDbType.BigInt).Value = ticketId;
        object? result = await cmd.ExecuteScalarAsync(ct);
        return result is null || result == DBNull.Value ? null : Convert.ToInt32(result);
    }

    public async Task<(int Status, string Message, long TicketId)> CreateAsync(int subId,
        int accountId, string? title, string? content, CancellationToken ct)
    {
        title = title?.Trim(); content = content?.Trim();
        if (string.IsNullOrWhiteSpace(title) || title.Length < 3 || title.Length > 160 ||
            string.IsNullOrWhiteSpace(content) || content.Length < 5 || content.Length > 2000)
            return (400, "Tiêu đề 1–160 ký tự và nội dung 1–2000 ký tự.", 0);
        await using var db = Connect();
        await db.OpenAsync(ct);
        await using var tx = (SqlTransaction)await db.BeginTransactionAsync(ct);
        await using var cmd = new SqlCommand("""
            INSERT dbo.YeuCauHoTro (MaThueBao,TieuDe) OUTPUT INSERTED.MaYeuCau
            VALUES (@SubId,@Title);
            """, db, tx);
        cmd.Parameters.Add("@SubId", SqlDbType.Int).Value = subId;
        cmd.Parameters.Add("@Title", SqlDbType.NVarChar, 160).Value = title;
        long id = Convert.ToInt64(await cmd.ExecuteScalarAsync(ct));
        await InsertMessage(db, tx, id, accountId, "USER", content, ct);
        await tx.CommitAsync(ct);
        return (201, "Đã gửi yêu cầu hỗ trợ tới quản trị viên.", id);
    }

    public async Task<(int Status, string Message)> ReplyAsync(long id, int accountId,
        int? ownerSubId, string role, string? message, CancellationToken ct)
    {
        message = message?.Trim();
        if (string.IsNullOrWhiteSpace(message) || message.Trim().Length < 5 || message.Length > 2000)
            return (400, "Nội dung phản hồi phải dài 1–2000 ký tự.");
        await using var db = Connect();
        await db.OpenAsync(ct);
        await using var tx = (SqlTransaction)await db.BeginTransactionAsync(ct);
        await using var cmd = new SqlCommand("""
            UPDATE dbo.YeuCauHoTro
            SET TrangThai=@Status, NgayCapNhat=SYSDATETIME()
            WHERE MaYeuCau=@Id AND (@SubId IS NULL OR MaThueBao=@SubId);
            """, db, tx);
        cmd.Parameters.Add("@Id", SqlDbType.BigInt).Value = id;
        cmd.Parameters.Add("@SubId", SqlDbType.Int).Value = (object?)ownerSubId ?? DBNull.Value;
        cmd.Parameters.Add("@Status", SqlDbType.VarChar, 12).Value = role == "ADMIN" ? "ANSWERED" : "OPEN";
        if (await cmd.ExecuteNonQueryAsync(ct) != 1)
        {
            await tx.RollbackAsync(ct);
            return (404, "Không tìm thấy yêu cầu hỗ trợ phù hợp.");
        }
        await InsertMessage(db, tx, id, accountId, role, message, ct);
        await tx.CommitAsync(ct);
        return (200, "Đã gửi phản hồi.");
    }

    public async Task<(int Status, string Message)> ChangeStatusAsync(long id, string? status,
        CancellationToken ct)
    {
        status = status?.Trim().ToUpperInvariant();
        if (status is not ("OPEN" or "ANSWERED" or "CLOSED"))
            return (400, "Trạng thái không hợp lệ.");
        await using var db = Connect();
        await db.OpenAsync(ct);
        await using var cmd = new SqlCommand("""
            UPDATE dbo.YeuCauHoTro SET TrangThai=@Status, NgayCapNhat=SYSDATETIME()
            WHERE MaYeuCau=@Id;
            """, db);
        cmd.Parameters.Add("@Id", SqlDbType.BigInt).Value = id;
        cmd.Parameters.Add("@Status", SqlDbType.VarChar, 12).Value = status;
        return await cmd.ExecuteNonQueryAsync(ct) == 1
            ? (200, "Đã cập nhật trạng thái yêu cầu hỗ trợ.")
            : (404, "Không tìm thấy yêu cầu hỗ trợ.");
    }

    private static async Task InsertMessage(SqlConnection db, SqlTransaction tx,
        long ticketId, int accountId, string role, string message, CancellationToken ct)
    {
        await using var cmd = new SqlCommand("""
            INSERT dbo.TinNhanHoTro(MaYeuCau,MaTaiKhoan,VaiTroNguoiGui,NoiDung)
            VALUES (@Ticket,@Account,@Role,@Message);
            """, db, tx);
        cmd.Parameters.Add("@Ticket", SqlDbType.BigInt).Value = ticketId;
        cmd.Parameters.Add("@Account", SqlDbType.Int).Value = accountId;
        cmd.Parameters.Add("@Role", SqlDbType.VarChar, 5).Value = role;
        cmd.Parameters.Add("@Message", SqlDbType.NVarChar, 2000).Value = message;
        await cmd.ExecuteNonQueryAsync(ct);
    }
}
