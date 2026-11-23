using System.Data;
using System.Net.Mail;
using System.Text.RegularExpressions;
using Microsoft.Data.SqlClient;

namespace MobileData.Api;

public sealed record UpdateOwnerProfileRequest(string? FullName, string? Gender,
    int? BirthYear, string? Hometown, string? Address, string? Email);
public sealed record RegisterOwnerRequest(string? Cccd, string? FullName, string? Gender,
    int? BirthYear, string? Hometown, string? Address, string? Email);
public sealed record OwnerPhotoRequest(string? PhotoBase64);
public sealed record AdminOwnerPhotoRequest(string? Cccd, string? PhotoBase64);
public sealed record AdminCustomerUpdateRequest(string? Cccd, string? FullName,
    string? Gender, int? BirthYear, string? Hometown, string? Address, string? Email);
public sealed record AdminCustomerDto(int Id, string Cccd, string FullName, string Gender,
    int? BirthYear, string Hometown, string Address, string Email, int SubscriberCount,
    string Phones, string Carriers, string Statuses);

public sealed class OwnerProfileService
{
    private readonly string _connectionString;
    public OwnerProfileService(IConfiguration configuration) =>
        _connectionString = configuration.GetConnectionString("MobileDataDB")
            ?? throw new InvalidOperationException("Thieu ConnectionStrings:MobileDataDB");

    private SqlConnection OpenConnection() => new(_connectionString);

    private static string? Verify(UpdateOwnerProfileRequest input)
    {
        string name = input.FullName?.Trim() ?? "";
        if (string.IsNullOrWhiteSpace(name)) return "Vui lòng nhập họ và tên.";
        if (name.Length > 100 ||
            !Regex.IsMatch(name, @"^[\p{L}\p{M}]+(?:[ '-]+[\p{L}\p{M}]+)+$"))
            return "Họ và tên cần ít nhất 2 từ và không được chứa số.";

        if (input.Gender is not ("Nam" or "Nữ" or "Khác"))
            return "Vui lòng chọn giới tính.";
        if (input.BirthYear is null) return "Vui lòng nhập năm sinh.";
        if (input.BirthYear < 1900 || input.BirthYear > DateTime.Now.Year)
            return "Năm sinh chưa hợp lệ.";

        static string? VerifyPlace(string? raw, int maxLength, string label)
        {
            string text = raw?.Trim() ?? "";
            if (text.Length == 0) return $"Vui lòng nhập {label}.";
            if (text.Length < 3 || text.Length > maxLength)
                return $"{label} cần từ 3 đến {maxLength} ký tự.";
            if (!text.Any(char.IsLetter))
                return $"{label} phải có chữ, không được chỉ nhập số hoặc ký hiệu.";
            if (text.Any(char.IsControl)) return $"{label} chứa ký tự không hợp lệ.";
            return null;
        }
        string? hometownError = VerifyPlace(input.Hometown, 120, "Quê quán");
        if (hometownError is not null) return hometownError;
        string? addressError = VerifyPlace(input.Address, 200, "Địa chỉ");
        if (addressError is not null) return addressError;

        string email = input.Email?.Trim() ?? "";
        if (email.Length == 0) return "Vui lòng nhập email.";
        if (email.Length > 150) return "Email không được vượt quá 150 ký tự.";
        try
        {
            var parsed = new MailAddress(email);
            if (!string.Equals(parsed.Address, email, StringComparison.OrdinalIgnoreCase)
                || !email.Contains('.')) return "Email không đúng định dạng.";
        }
        catch { return "Email không đúng định dạng."; }
        return null;
    }

    private static void BindFields(SqlCommand cmd, UpdateOwnerProfileRequest input)
    {
        cmd.Parameters.Add("@Name", SqlDbType.NVarChar, 100).Value = input.FullName!.Trim();
        cmd.Parameters.Add("@Gender", SqlDbType.NVarChar, 12).Value = string.IsNullOrWhiteSpace(input.Gender) ? DBNull.Value : input.Gender.Trim();
        cmd.Parameters.Add("@Year", SqlDbType.SmallInt).Value = input.BirthYear is null ? DBNull.Value : input.BirthYear.Value;
        cmd.Parameters.Add("@Hometown", SqlDbType.NVarChar, 120).Value = string.IsNullOrWhiteSpace(input.Hometown) ? DBNull.Value : input.Hometown.Trim();
        cmd.Parameters.Add("@Address", SqlDbType.NVarChar, 200).Value = string.IsNullOrWhiteSpace(input.Address) ? DBNull.Value : input.Address.Trim();
        cmd.Parameters.Add("@Email", SqlDbType.VarChar, 150).Value = string.IsNullOrWhiteSpace(input.Email) ? DBNull.Value : input.Email.Trim();
    }

    public async Task<object?> GetBySubscriberAsync(int subscriberId, CancellationToken ct)
    {
        const string sql = """
            SELECT kh.CCCD, kh.HoTen, kh.GioiTinh, kh.NamSinh, kh.QueQuan, kh.DiaChi,
                kh.Email, kh.AnhDaiDien, tb.SoDienThoai, nm.TenNhaMang
            FROM dbo.KhachHang kh
            JOIN dbo.ThueBao tb ON tb.MaKhachHang = kh.MaKhachHang
            JOIN dbo.NhaMang nm ON nm.MaNhaMang = tb.MaNhaMang
            WHERE tb.MaThueBao=@Id;
            """;
        await using var db = OpenConnection();
        await db.OpenAsync(ct);
        await using var cmd = new SqlCommand(sql, db);
        cmd.Parameters.Add("@Id", SqlDbType.Int).Value = subscriberId;
        await using var reader = await cmd.ExecuteReaderAsync(ct);
        if (!await reader.ReadAsync(ct)) return null;
        return new {
            cccd = reader.GetString(0), fullName = reader.GetString(1),
            gender = reader.IsDBNull(2) ? "" : reader.GetString(2),
            birthYear = reader.IsDBNull(3) ? (int?)null : reader.GetInt16(3),
            hometown = reader.IsDBNull(4) ? "" : reader.GetString(4),
            address = reader.IsDBNull(5) ? "" : reader.GetString(5),
            email = reader.IsDBNull(6) ? "" : reader.GetString(6),
            photoBase64 = reader.IsDBNull(7) ? "" : Convert.ToBase64String((byte[]) reader.GetValue(7)),
            phone = reader.GetString(8), carrierName = reader.GetString(9)
        };
    }

    public async Task<(int Status, string Message)> UpdateBySubscriberAsync(int subscriberId,
        UpdateOwnerProfileRequest input, CancellationToken ct)
    {
        string? validation = Verify(input);
        if (validation is not null) return (400, validation);
        const string sql = """
            UPDATE kh SET HoTen=@Name, GioiTinh=@Gender, NamSinh=@Year,
                QueQuan=@Hometown, DiaChi=@Address, Email=@Email
            FROM dbo.KhachHang kh
            JOIN dbo.ThueBao tb ON tb.MaKhachHang=kh.MaKhachHang
            WHERE tb.MaThueBao=@Id;
            """;
        await using var db = OpenConnection();
        await db.OpenAsync(ct);
        await using var cmd = new SqlCommand(sql, db);
        BindFields(cmd, input);
        cmd.Parameters.Add("@Id", SqlDbType.Int).Value = subscriberId;
        int count = await cmd.ExecuteNonQueryAsync(ct);
        return count == 0 ? (404, "Khong tim thay ho so.") : (200, "Da cap nhat ho so chu thue bao.");
    }

    public async Task<(int Status, string Message)> UpdatePhotoAsync(int subscriberId,
        OwnerPhotoRequest input, CancellationToken ct)
    {
        byte[] photo;
        try { photo = Convert.FromBase64String(input.PhotoBase64 ?? ""); }
        catch (FormatException) { return (400, "Anh khong dung dinh dang Base64."); }
        if (photo.Length < 100 || photo.Length > 256 * 1024)
            return (400, "Anh phai nho hon 256 KB.");
        bool jpeg = photo[0] == 0xff && photo[1] == 0xd8;
        bool png = photo.Length > 8 && photo[0] == 0x89 && photo[1] == 0x50 && photo[2] == 0x4e && photo[3] == 0x47;
        if (!jpeg && !png) return (400, "Chi ho tro anh JPEG hoac PNG.");
        const string sql = """
            UPDATE kh SET AnhDaiDien=@Photo
            FROM dbo.KhachHang kh
            JOIN dbo.ThueBao tb ON tb.MaKhachHang=kh.MaKhachHang
            WHERE tb.MaThueBao=@Id;
            """;
        await using var db = OpenConnection();
        await db.OpenAsync(ct);
        await using var cmd = new SqlCommand(sql, db);
        cmd.Parameters.Add("@Photo", SqlDbType.VarBinary, -1).Value = photo;
        cmd.Parameters.Add("@Id", SqlDbType.Int).Value = subscriberId;
        int count = await cmd.ExecuteNonQueryAsync(ct);
        return count == 0 ? (404, "Khong tim thay ho so.") : (200, "Da doi anh dai dien.");
    }

    public async Task<(int Status, string Message)> UpdateOwnerPhotoByCccdAsync(
        AdminOwnerPhotoRequest input, CancellationToken ct)
    {
        if (input.Cccd is null || input.Cccd.Trim().Length != 12 ||
            !input.Cccd.Trim().All(char.IsAsciiDigit))
            return (400, "CCCD khong hop le.");
        byte[] photo;
        try { photo = Convert.FromBase64String(input.PhotoBase64 ?? ""); }
        catch (FormatException) { return (400, "Anh khong dung dinh dang."); }
        if (photo.Length < 100 || photo.Length > 256 * 1024)
            return (400, "Anh phai nho hon 256 KB.");
        bool jpeg = photo[0] == 0xff && photo[1] == 0xd8;
        bool png = photo.Length > 8 && photo[0] == 0x89 && photo[1] == 0x50 && photo[2] == 0x4e && photo[3] == 0x47;
        if (!jpeg && !png) return (400, "Chi ho tro anh JPEG hoac PNG.");
        await using var db = OpenConnection();
        await db.OpenAsync(ct);
        await using var cmd = new SqlCommand("UPDATE dbo.KhachHang SET AnhDaiDien=@Photo WHERE CCCD=@Cccd", db);
        cmd.Parameters.Add("@Photo", SqlDbType.VarBinary, -1).Value = photo;
        cmd.Parameters.Add("@Cccd", SqlDbType.VarChar, 12).Value = input.Cccd.Trim();
        return await cmd.ExecuteNonQueryAsync(ct) > 0 ? (200, "Da luu anh dai dien.") : (404, "Khong tim thay CCCD.");
    }

    public async Task<(int Status, string Message)> RegisterOwnerAsync(RegisterOwnerRequest input,
        CancellationToken ct)
    {
        if (input.Cccd is null || input.Cccd.Trim().Length != 12 || !input.Cccd.Trim().All(char.IsAsciiDigit))
            return (400, "CCCD phải có đúng 12 chữ số.");
        string? validation = Verify(new UpdateOwnerProfileRequest(input.FullName, input.Gender,
            input.BirthYear, input.Hometown, input.Address, input.Email));
        if (validation is not null) return (400, validation);
        await using var db = OpenConnection();
        await db.OpenAsync(ct);
        const string sql = """
            INSERT dbo.KhachHang (CCCD, HoTen, GioiTinh, NamSinh, QueQuan, DiaChi, Email)
            VALUES (@Cccd, @Name, @Gender, @Year, @Hometown, @Address, @Email);
            """;
        await using var cmd = new SqlCommand(sql, db);
        cmd.Parameters.Add("@Cccd", SqlDbType.VarChar, 12).Value = input.Cccd.Trim();
        BindFields(cmd, new UpdateOwnerProfileRequest(input.FullName, input.Gender,
            input.BirthYear, input.Hometown, input.Address, input.Email));
        try { await cmd.ExecuteNonQueryAsync(ct); }
        catch (SqlException ex) when (ex.Number is 2601 or 2627) { return (409, "CCCD này đã được đăng ký."); }
        return (201, "Đã thêm khách hàng. Bạn có thể thêm số điện thoại tại mục Quản lý thuê bao.");
    }

    // Danh sach ho so theo MaKhachHang, khong gui CCCD, anh hoac du lieu ca nhan cho USER khac.
    public async Task<IReadOnlyList<AdminCustomerDto>> ListCustomersAsync(CancellationToken ct)
    {
        const string sql = """
            SELECT kh.MaKhachHang, kh.CCCD, kh.HoTen, kh.GioiTinh, kh.NamSinh,
                   kh.QueQuan, kh.DiaChi, kh.Email,
                   (SELECT COUNT(*) FROM dbo.ThueBao tb WHERE tb.MaKhachHang=kh.MaKhachHang),
                   ISNULL(summary.Phones, ''), ISNULL(summary.Carriers, ''),
                   ISNULL(summary.Statuses, '')
            FROM dbo.KhachHang kh
            OUTER APPLY (
                SELECT STRING_AGG(CONVERT(nvarchar(max), tb.SoDienThoai), N', ') AS Phones,
                       STRING_AGG(CONVERT(nvarchar(max), nm.TenNhaMang), N', ') AS Carriers,
                       STRING_AGG(CONVERT(nvarchar(max), CASE
                            WHEN tb.TrangThai = 'ACTIVE' AND tk.DangHoatDong = 1 THEN 'ACTIVE'
                            ELSE 'LOCKED' END), N', ') AS Statuses
                FROM dbo.ThueBao tb
                INNER JOIN dbo.NhaMang nm ON nm.MaNhaMang = tb.MaNhaMang
                LEFT JOIN dbo.TaiKhoan tk ON tk.MaThueBao = tb.MaThueBao
                WHERE tb.MaKhachHang = kh.MaKhachHang
            ) summary
            ORDER BY kh.MaKhachHang DESC;
            """;
        var result = new List<AdminCustomerDto>();
        await using var db = OpenConnection();
        await db.OpenAsync(ct);
        await using var cmd = new SqlCommand(sql, db);
        await using var r = await cmd.ExecuteReaderAsync(ct);
        while (await r.ReadAsync(ct)) result.Add(new AdminCustomerDto(
            r.GetInt32(0), r.GetString(1), r.GetString(2),
            r.IsDBNull(3) ? "" : r.GetString(3),
            r.IsDBNull(4) ? null : r.GetInt16(4),
            r.IsDBNull(5) ? "" : r.GetString(5),
            r.IsDBNull(6) ? "" : r.GetString(6),
            r.IsDBNull(7) ? "" : r.GetString(7), r.GetInt32(8),
            r.GetString(9), r.GetString(10), r.GetString(11)));
        return result;
    }

    // Cho phep ADMIN sua CCCD nhap sai. Quan he FK dung MaKhachHang nen khong bi dut.
    public async Task<(int Status, string Message)> UpdateCustomerAsync(int id,
        AdminCustomerUpdateRequest input, CancellationToken ct)
    {
        string cccd = input.Cccd?.Trim() ?? "";
        if (cccd.Length != 12 || !cccd.All(char.IsAsciiDigit))
            return (400, "CCCD phải có đúng 12 chữ số.");
        var profile = new UpdateOwnerProfileRequest(input.FullName, input.Gender,
            input.BirthYear, input.Hometown, input.Address, input.Email);
        string? validation = Verify(profile);
        if (validation is not null) return (400, validation);
        const string sql = """
            UPDATE dbo.KhachHang SET CCCD=@Cccd, HoTen=@Name, GioiTinh=@Gender,
                NamSinh=@Year, QueQuan=@Hometown, DiaChi=@Address, Email=@Email
            WHERE MaKhachHang=@Id;
            """;
        await using var db = OpenConnection();
        await db.OpenAsync(ct);
        await using var cmd = new SqlCommand(sql, db);
        cmd.Parameters.Add("@Id", SqlDbType.Int).Value = id;
        cmd.Parameters.Add("@Cccd", SqlDbType.VarChar, 12).Value = cccd;
        BindFields(cmd, profile);
        try
        {
            int changed = await cmd.ExecuteNonQueryAsync(ct);
            return changed > 0 ? (200, "Đã lưu thay đổi.")
                : (404, "Khong tim thay khach hang.");
        }
        catch (SqlException e) when (e.Number is 2601 or 2627)
        {
            return (409, "CCCD này đã thuộc về khách hàng khác.");
        }
    }

    // Khong xoa ho so dang co so dien thoai; bao toan lich su giao dich va ho tro.
    public async Task<(int Status, string Message)> DeleteCustomerAsync(int id, CancellationToken ct)
    {
        await using var db = OpenConnection();
        await db.OpenAsync(ct);
        await using var tx = (SqlTransaction)await db.BeginTransactionAsync(IsolationLevel.Serializable, ct);
        await using var check = new SqlCommand("""
            SELECT COUNT(*) FROM dbo.ThueBao WITH (UPDLOCK, HOLDLOCK)
            WHERE MaKhachHang=@Id;
            """, db, tx);
        check.Parameters.Add("@Id", SqlDbType.Int).Value = id;
        if (Convert.ToInt32(await check.ExecuteScalarAsync(ct)) > 0)
        {
            await tx.RollbackAsync(ct);
            return (409, "Khach hang dang co thue bao. Hay khoa thue bao tai muc Quan ly thue bao; khong xoa de giu lich su giao dich.");
        }
        await using var del = new SqlCommand("DELETE dbo.KhachHang WHERE MaKhachHang=@Id", db, tx);
        del.Parameters.Add("@Id", SqlDbType.Int).Value = id;
        try
        {
            int removed = await del.ExecuteNonQueryAsync(ct);
            await tx.CommitAsync(ct);
            return removed == 0 ? (404, "Khong tim thay khach hang.") : (200, "Đã xóa hồ sơ khách hàng.");
        }
        catch (SqlException e) when (e.Number == 547)
        {
            await tx.RollbackAsync(ct);
            return (409, "Khong the xoa ho so vi van co du lieu lien ket.");
        }
    }

}
