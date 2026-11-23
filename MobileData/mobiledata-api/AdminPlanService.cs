using System.Data;
using System.Text.RegularExpressions;
using Microsoft.Data.SqlClient;

namespace MobileData.Api;

// Quyền và vai trò được xác minh trong DemoAuthService trước khi gọi các hàm này.
public sealed record AdminPlanDto(
    int Id,
    string CarrierCode,
    string CarrierName,
    string Code,
    string Name,
    decimal Price,
    decimal DataGb,
    string DataType,
    int DurationDays,
    string? Description,
    bool IsPopular,
    bool IsForSale,
    DateTime CreatedAt
);

public sealed record NewPlanRequest(
    string? CarrierCode,
    string? Code,
    string? Name,
    decimal Price,
    decimal DataGb,
    string? DataType,
    int DurationDays,
    string? Description,
    bool IsPopular,
    bool IsForSale
);

public sealed record UpdatePlanRequest(
    string? Name,
    decimal Price,
    decimal DataGb,
    string? DataType,
    int DurationDays,
    string? Description,
    bool IsPopular,
    bool IsForSale
);

public sealed record PlanSaleStatusRequest(bool IsForSale);

public sealed class AdminPlanService
{
    private readonly string _connectionString;
    private static readonly Regex CodeRegex = new("^[A-Z0-9_-]{2,20}$", RegexOptions.Compiled);

    public AdminPlanService(IConfiguration config)
    {
        _connectionString = config.GetConnectionString("MobileDataDB")
            ?? throw new InvalidOperationException("Thiếu chuỗi kết nối MobileDataDB.");
    }

    private SqlConnection NewConnection() => new(_connectionString);

    public static string? Validate(NewPlanRequest p)
    {
        string carrier = (p.CarrierCode ?? "").Trim().ToUpperInvariant();
        string code = (p.Code ?? "").Trim().ToUpperInvariant();
        if (carrier is not ("VT" or "VN" or "MB")) return "Mã nhà mạng phải là VT, VN hoặc MB.";
        if (!CodeRegex.IsMatch(code)) return "Mã gói phải dài 2–20 ký tự A-Z, 0-9, _ hoặc -.";
        return ValidateFields(p.Name, p.Price, p.DataGb, p.DataType, p.DurationDays, p.Description);
    }

    public static string? Validate(UpdatePlanRequest p) =>
        ValidateFields(p.Name, p.Price, p.DataGb, p.DataType, p.DurationDays, p.Description);

    private static string? ValidateFields(string? name, decimal price, decimal dataGb,
        string? dataType, int duration, string? description)
    {
        if (string.IsNullOrWhiteSpace(name) || name.Trim().Length > 100)
            return "Tên gói không được trống và tối đa 100 ký tự.";
        if (price <= 0 || price > 1_000_000_000 || decimal.Truncate(price) != price)
            return "Giá gói phải là số tiền VND nguyên từ 1 đến 1 tỷ.";
        if (dataGb <= 0 || dataGb > 100_000 || decimal.Round(dataGb, 2) != dataGb)
            return "Dung lượng phải lớn hơn 0, tối đa 100.000 GB và tối đa 2 chữ số thập phân.";
        if ((dataType ?? "").Trim().ToUpperInvariant() is not ("DAILY" or "TOTAL"))
            return "Kiểu dung lượng phải là DAILY hoặc TOTAL.";
        if (duration < 1 || duration > 3650)
            return "Chu kỳ phải từ 1 đến 3650 ngày.";
        if (description is { Length: > 500 })
            return "Mô tả tối đa 500 ký tự.";
        return null;
    }

    private static void AddWriteParams(SqlCommand cmd, string name, decimal price,
        decimal dataGb, string dataType, int days, string? description, bool popular, bool forSale)
    {
        cmd.Parameters.Add("@Name", SqlDbType.NVarChar, 100).Value = name.Trim();
        cmd.Parameters.Add(new SqlParameter("@Price", SqlDbType.Decimal)
            { Precision = 18, Scale = 0, Value = price });
        cmd.Parameters.Add(new SqlParameter("@DataGb", SqlDbType.Decimal)
            { Precision = 10, Scale = 2, Value = dataGb });
        cmd.Parameters.Add("@DataType", SqlDbType.VarChar, 10).Value = dataType.Trim().ToUpperInvariant();
        cmd.Parameters.Add("@Days", SqlDbType.Int).Value = days;
        cmd.Parameters.Add("@Description", SqlDbType.NVarChar, 500).Value =
            string.IsNullOrWhiteSpace(description) ? DBNull.Value : description.Trim();
        cmd.Parameters.Add("@Popular", SqlDbType.Bit).Value = popular;
        cmd.Parameters.Add("@ForSale", SqlDbType.Bit).Value = forSale;
    }

    public async Task<IReadOnlyList<AdminPlanDto>> ListAsync(string? carrier, CancellationToken ct)
    {
        const string sql = """
            SELECT gc.MaGoi, gc.MaNhaMang, nm.TenNhaMang, gc.MaGoiHienThi,
                   gc.TenGoi, gc.Gia, gc.DungLuongGB, gc.KieuDungLuong,
                   gc.ChuKyNgay, gc.MoTa, gc.PhoBien, gc.DangBan, gc.NgayTao
            FROM dbo.GoiCuoc AS gc
            JOIN dbo.NhaMang AS nm ON nm.MaNhaMang = gc.MaNhaMang
            WHERE @Carrier IS NULL OR gc.MaNhaMang = @Carrier
            ORDER BY gc.MaNhaMang, gc.MaGoi;
            """;
        var plans = new List<AdminPlanDto>();
        await using var db = NewConnection();
        await db.OpenAsync(ct);
        await using var cmd = new SqlCommand(sql, db);
        cmd.Parameters.Add("@Carrier", SqlDbType.VarChar, 5).Value =
            string.IsNullOrWhiteSpace(carrier) ? DBNull.Value : carrier.Trim().ToUpperInvariant();
        await using var reader = await cmd.ExecuteReaderAsync(ct);
        while (await reader.ReadAsync(ct))
        {
            plans.Add(new AdminPlanDto(
                reader.GetInt32(0), reader.GetString(1), reader.GetString(2),
                reader.GetString(3), reader.GetString(4), reader.GetDecimal(5),
                reader.GetDecimal(6), reader.GetString(7), reader.GetInt32(8),
                reader.IsDBNull(9) ? null : reader.GetString(9),
                reader.GetBoolean(10), reader.GetBoolean(11), reader.GetDateTime(12)
            ));
        }
        return plans;
    }

    // null => mã nhà mạng không tồn tại / ngừng hoạt động.
    public async Task<int?> CreateAsync(NewPlanRequest p, CancellationToken ct)
    {
        const string sql = """
            INSERT INTO dbo.GoiCuoc
                (MaNhaMang, MaGoiHienThi, TenGoi, Gia, DungLuongGB,
                 KieuDungLuong, ChuKyNgay, MoTa, PhoBien, DangBan)
            OUTPUT INSERTED.MaGoi
            SELECT nm.MaNhaMang, @Code, @Name, @Price, @DataGb,
                   @DataType, @Days, @Description, @Popular, @ForSale
            FROM dbo.NhaMang AS nm
            WHERE nm.MaNhaMang = @Carrier AND nm.DangHoatDong = 1;
            """;
        await using var db = NewConnection();
        await db.OpenAsync(ct);
        await using var cmd = new SqlCommand(sql, db);
        cmd.Parameters.Add("@Carrier", SqlDbType.VarChar, 5).Value = p.CarrierCode!.Trim().ToUpperInvariant();
        cmd.Parameters.Add("@Code", SqlDbType.VarChar, 20).Value = p.Code!.Trim().ToUpperInvariant();
        AddWriteParams(cmd, p.Name!, p.Price, p.DataGb, p.DataType!,
            p.DurationDays, p.Description, p.IsPopular, p.IsForSale);
        object? id = await cmd.ExecuteScalarAsync(ct);
        return id is null ? null : Convert.ToInt32(id);
    }

    // Không đổi MaGoi, MaNhaMang, MaGoiHienThi để giữ ổn định lịch sử.
    public async Task<bool> UpdateAsync(int id, UpdatePlanRequest p, CancellationToken ct)
    {
        const string sql = """
            UPDATE dbo.GoiCuoc
            SET TenGoi = @Name, Gia = @Price, DungLuongGB = @DataGb,
                KieuDungLuong = @DataType, ChuKyNgay = @Days,
                MoTa = @Description, PhoBien = @Popular, DangBan = @ForSale
            WHERE MaGoi = @Id;
            """;
        await using var db = NewConnection();
        await db.OpenAsync(ct);
        await using var cmd = new SqlCommand(sql, db);
        cmd.Parameters.Add("@Id", SqlDbType.Int).Value = id;
        AddWriteParams(cmd, p.Name!, p.Price, p.DataGb, p.DataType!,
            p.DurationDays, p.Description, p.IsPopular, p.IsForSale);
        return await cmd.ExecuteNonQueryAsync(ct) > 0;
    }

    // Không xóa cứng gói: ngừng bán để các đăng ký cũ vẫn tham chiếu được.
    public async Task<bool> SetSaleStatusAsync(int id, bool forSale, CancellationToken ct)
    {
        const string sql = "UPDATE dbo.GoiCuoc SET DangBan = @ForSale WHERE MaGoi = @Id;";
        await using var db = NewConnection();
        await db.OpenAsync(ct);
        await using var cmd = new SqlCommand(sql, db);
        cmd.Parameters.Add("@Id", SqlDbType.Int).Value = id;
        cmd.Parameters.Add("@ForSale", SqlDbType.Bit).Value = forSale;
        return await cmd.ExecuteNonQueryAsync(ct) > 0;
    }
}
