using System.Data;
using Microsoft.Data.SqlClient;

namespace MobileData.Api;

public sealed record CreateSubscriberRequest(string? Cccd, string? FullName,
    string? Phone, string? CarrierCode);
public sealed record EditSubscriberRequest(string? FullName, string? Phone, bool Locked);
public sealed record SubscriberDto(int Id, string Cccd, string FullName, string Phone,
    string CarrierCode, string CarrierName, decimal Balance,
    string Status, string Role, bool AccountActive);

public sealed class MobileDataSubscriberService
{
    private readonly string _connectionString;
    public MobileDataSubscriberService(IConfiguration config) =>
        _connectionString = config.GetConnectionString("MobileDataDB")
            ?? throw new InvalidOperationException("Thiếu chuỗi kết nối MobileDataDB.");

    private SqlConnection NewConnection() => new(_connectionString);
    private static bool IsPhone(string? s) => s is { Length: 10 } &&
        s[0] == '0' && "35789".Contains(s[1]) && s.All(char.IsAsciiDigit);
    private static bool IsCccd(string? s) => s is { Length: 12 } && s.All(char.IsAsciiDigit);

    public static string? Validate(CreateSubscriberRequest p)
    {
        if (!IsCccd(p.Cccd?.Trim())) return "CCCD phải có đúng 12 chữ số.";
        if (string.IsNullOrWhiteSpace(p.FullName) || p.FullName.Trim().Length < 2 || p.FullName.Trim().Length > 100 || !p.FullName.Any(char.IsLetter))
            return "Họ tên cần từ 2 đến 100 ký tự.";
        if (!IsPhone(p.Phone?.Trim())) return "Số điện thoại cần 10 chữ số, bắt đầu bằng 03, 05, 07, 08 hoặc 09.";
        if (p.CarrierCode?.Trim().ToUpperInvariant() is not ("VN" or "VT" or "MB"))
            return "Nhà mạng phải là VN, VT hoặc MB.";
        return null;
    }

    public static string? Validate(EditSubscriberRequest p)
    {
        if (string.IsNullOrWhiteSpace(p.FullName) || p.FullName.Trim().Length < 2 || p.FullName.Trim().Length > 100 || !p.FullName.Any(char.IsLetter))
            return "Họ tên cần từ 2 đến 100 ký tự.";
        if (!IsPhone(p.Phone?.Trim())) return "Số điện thoại cần 10 chữ số, bắt đầu bằng 03, 05, 07, 08 hoặc 09.";
        return null;
    }

    public async Task<IReadOnlyList<SubscriberDto>> ListAsync(CancellationToken ct)
    {
        const string sql = """
            SELECT tb.MaThueBao, kh.CCCD, kh.HoTen, tb.SoDienThoai,
                tb.MaNhaMang, nm.TenNhaMang, tb.SoDu, tb.TrangThai,
                tk.VaiTro, tk.DangHoatDong
            FROM dbo.ThueBao AS tb
            JOIN dbo.KhachHang AS kh ON kh.MaKhachHang=tb.MaKhachHang
            JOIN dbo.NhaMang AS nm ON nm.MaNhaMang=tb.MaNhaMang
            JOIN dbo.TaiKhoan AS tk ON tk.MaThueBao=tb.MaThueBao
            ORDER BY kh.HoTen, tb.MaNhaMang;
            """;
        var result = new List<SubscriberDto>();
        await using var db = NewConnection();
        await db.OpenAsync(ct);
        await using var cmd = new SqlCommand(sql, db);
        await using var reader = await cmd.ExecuteReaderAsync(ct);
        while (await reader.ReadAsync(ct))
            result.Add(new SubscriberDto(reader.GetInt32(0), reader.GetString(1),
                reader.GetString(2), reader.GetString(3), reader.GetString(4),
                reader.GetString(5), reader.GetDecimal(6), reader.GetString(7),
                reader.GetString(8), reader.GetBoolean(9)));
        return result;
    }

    // Tạo tài khoản USER và thuê bao trong cùng transaction.
    // SERIALIZABLE + khóa hàng CCCD kết hợp unique index & trigger để hạn chế cấp trùng.
    public async Task<(int Status, string Message, int SubscriberId)> CreateAsync(
        CreateSubscriberRequest input, CancellationToken ct)
    {
        string cccd = input.Cccd!.Trim(), phone = input.Phone!.Trim();
        string carrier = input.CarrierCode!.Trim().ToUpperInvariant();
        string name = input.FullName!.Trim();
        await using var db = NewConnection();
        await db.OpenAsync(ct);
        await using var tx = (SqlTransaction)await db.BeginTransactionAsync(IsolationLevel.Serializable, ct);
        int customerId;
        await using (var find = new SqlCommand("""
            SELECT MaKhachHang FROM dbo.KhachHang WITH (UPDLOCK, HOLDLOCK)
            WHERE CCCD=@Cccd;
            """, db, tx))
        {
            find.Parameters.Add("@Cccd", SqlDbType.VarChar, 12).Value = cccd;
            object? existing = await find.ExecuteScalarAsync(ct);
            if (existing is null)
            {
                await using var insert = new SqlCommand("""
                    INSERT dbo.KhachHang (CCCD, HoTen)
                    OUTPUT INSERTED.MaKhachHang VALUES (@Cccd, @Name);
                    """, db, tx);
                insert.Parameters.Add("@Cccd", SqlDbType.VarChar, 12).Value = cccd;
                insert.Parameters.Add("@Name", SqlDbType.NVarChar, 100).Value = name;
                customerId = Convert.ToInt32(await insert.ExecuteScalarAsync(ct));
            }
            else customerId = Convert.ToInt32(existing);
        }
        await using (var count = new SqlCommand("""
            SELECT COUNT(*) FROM dbo.ThueBao WITH (UPDLOCK, HOLDLOCK)
            WHERE MaKhachHang=@Id;
            """, db, tx))
        {
            count.Parameters.Add("@Id", SqlDbType.Int).Value = customerId;
            if (Convert.ToInt32(await count.ExecuteScalarAsync(ct)) >= 3)
            {
                await tx.RollbackAsync(ct);
                return (409, "Mỗi CCCD được đăng ký tối đa 3 số điện thoại.", 0);
            }
        }
        await using (var exists = new SqlCommand("""
            SELECT COUNT(*) FROM dbo.ThueBao WITH (UPDLOCK,HOLDLOCK)
            WHERE MaKhachHang=@Id AND MaNhaMang=@Carrier;
            """, db, tx))
        {
            exists.Parameters.Add("@Id", SqlDbType.Int).Value = customerId;
            exists.Parameters.Add("@Carrier", SqlDbType.VarChar, 5).Value = carrier;
            if (Convert.ToInt32(await exists.ExecuteScalarAsync(ct)) > 0)
            {
                await tx.RollbackAsync(ct);
                return (409, "CCCD đã có số của nhà mạng này.", 0);
            }
        }
        const string sql = """
            INSERT dbo.ThueBao (MaKhachHang,MaNhaMang,SoDienThoai)
            SELECT @CustomerId, nm.MaNhaMang, @Phone FROM dbo.NhaMang AS nm
            WHERE nm.MaNhaMang=@Carrier AND nm.DangHoatDong=1;
            SELECT CAST(SCOPE_IDENTITY() AS INT);
            """;
        int id;
        await using (var insert = new SqlCommand(sql, db, tx))
        {
            insert.Parameters.Add("@CustomerId", SqlDbType.Int).Value = customerId;
            insert.Parameters.Add("@Carrier", SqlDbType.VarChar, 5).Value = carrier;
            insert.Parameters.Add("@Phone", SqlDbType.VarChar, 10).Value = phone;
            object? result = await insert.ExecuteScalarAsync(ct);
            if (result is null)
            {
                await tx.RollbackAsync(ct);
                return (400, "Nhà mạng không hoạt động hoặc không tồn tại.", 0);
            }
            id = Convert.ToInt32(result);
        }
        await using (var account = new SqlCommand("""
            INSERT dbo.TaiKhoan (MaThueBao, VaiTro) VALUES (@Id, 'USER');
            """, db, tx))
        {
            account.Parameters.Add("@Id", SqlDbType.Int).Value = id;
            await account.ExecuteNonQueryAsync(ct);
        }
        await tx.CommitAsync(ct);
        return (201, "Đã thêm thuê bao và tài khoản USER.", id);
    }

    // Chỉ được sửa/khóa USER. CCCD và nhà mạng không đổi để giữ quan hệ dữ liệu.
    // Họ tên thuộc KhachHang, thay đổi sẽ hiển thị ở tất cả số của cùng CCCD.
    public async Task<(int Status, string Message)> EditAsync(
        int id, EditSubscriberRequest input, CancellationToken ct)
    {
        await using var db = NewConnection();
        await db.OpenAsync(ct);
        await using var tx = (SqlTransaction)await db.BeginTransactionAsync(IsolationLevel.Serializable, ct);
        int customerId;
        await using (var find = new SqlCommand("""
            SELECT tb.MaKhachHang FROM dbo.ThueBao AS tb WITH (UPDLOCK,HOLDLOCK)
            JOIN dbo.TaiKhoan AS tk ON tk.MaThueBao=tb.MaThueBao
            WHERE tb.MaThueBao=@Id AND tk.VaiTro='USER';
            """, db, tx))
        {
            find.Parameters.Add("@Id", SqlDbType.Int).Value = id;
            object? result = await find.ExecuteScalarAsync(ct);
            if (result is null)
            {
                await tx.RollbackAsync(ct);
                return (404, "Không tìm thấy thuê bao USER cần chỉnh sửa.");
            }
            customerId = Convert.ToInt32(result);
        }
        await using (var name = new SqlCommand("""
            UPDATE dbo.KhachHang SET HoTen=@Name WHERE MaKhachHang=@Id;
            """, db, tx))
        {
            name.Parameters.Add("@Name", SqlDbType.NVarChar, 100).Value = input.FullName!.Trim();
            name.Parameters.Add("@Id", SqlDbType.Int).Value = customerId;
            await name.ExecuteNonQueryAsync(ct);
        }
        await using (var update = new SqlCommand("""
            UPDATE dbo.ThueBao SET SoDienThoai=@Phone,
                TrangThai=@Status WHERE MaThueBao=@Id;
            """, db, tx))
        {
            update.Parameters.Add("@Phone", SqlDbType.VarChar, 10).Value = input.Phone!.Trim();
            update.Parameters.Add("@Status", SqlDbType.VarChar, 12).Value = input.Locked ? "LOCKED" : "ACTIVE";
            update.Parameters.Add("@Id", SqlDbType.Int).Value = id;
            await update.ExecuteNonQueryAsync(ct);
        }
        await tx.CommitAsync(ct);
        return (200, "Đã cập nhật thuê bao.");
    }
}
