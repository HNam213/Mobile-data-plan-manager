using System.Data;
using Microsoft.Data.SqlClient;

namespace MobileData.Api;

public sealed class MobileDataRepository
{
    private readonly string _connectionString;

    public MobileDataRepository(IConfiguration configuration)
    {
        _connectionString = configuration.GetConnectionString("MobileDataDB")
            ?? throw new InvalidOperationException("Chưa cấu hình ConnectionStrings:MobileDataDB.");
    }

    // Kết nối thật tới SQL Server; SELECT 1 chỉ để kiểm tra kết nối.
    public async Task CheckConnectionAsync(CancellationToken cancellationToken)
    {
        await using var connection = new SqlConnection(_connectionString);
        await connection.OpenAsync(cancellationToken);
        await using var command = new SqlCommand("SELECT 1", connection);
        await command.ExecuteScalarAsync(cancellationToken);
    }

    // Chỉ đọc danh sách nhà mạng đang hoạt động.
    public async Task<IReadOnlyList<CarrierDto>> GetCarriersAsync(CancellationToken cancellationToken)
    {
        const string sql = """
            SELECT MaNhaMang, TenNhaMang
            FROM dbo.NhaMang
            WHERE DangHoatDong = 1
            ORDER BY TenNhaMang;
            """;

        var result = new List<CarrierDto>();
        await using var connection = new SqlConnection(_connectionString);
        await connection.OpenAsync(cancellationToken);
        await using var command = new SqlCommand(sql, connection);
        await using var reader = await command.ExecuteReaderAsync(cancellationToken);

        while (await reader.ReadAsync(cancellationToken))
        {
            result.Add(new CarrierDto(
                reader.GetString(reader.GetOrdinal("MaNhaMang")),
                reader.GetString(reader.GetOrdinal("TenNhaMang"))
            ));
        }

        return result;
    }

    // Lọc gói còn bán bằng SQL có tham số để tránh SQL Injection.
    public async Task<IReadOnlyList<PlanDto>> GetPlansAsync(
        string? carrier, string? search, CancellationToken cancellationToken)
    {
        const string sql = """
            SELECT gc.MaGoi,
                   gc.MaNhaMang,
                   nm.TenNhaMang,
                   gc.MaGoiHienThi,
                   gc.TenGoi,
                   gc.Gia,
                   gc.DungLuongGB,
                   gc.KieuDungLuong,
                   gc.ChuKyNgay,
                   gc.MoTa,
                   gc.PhoBien
            FROM dbo.GoiCuoc AS gc
            INNER JOIN dbo.NhaMang AS nm ON gc.MaNhaMang = nm.MaNhaMang
            WHERE gc.DangBan = 1
              AND nm.DangHoatDong = 1
              AND (@Carrier IS NULL OR gc.MaNhaMang = @Carrier)
              AND (@Search IS NULL
                   OR gc.MaGoiHienThi LIKE N'%' + @Search + N'%'
                   OR gc.TenGoi LIKE N'%' + @Search + N'%'
                   OR gc.MoTa LIKE N'%' + @Search + N'%')
            ORDER BY gc.PhoBien DESC, gc.Gia, gc.MaGoi;
            """;

        var result = new List<PlanDto>();
        await using var connection = new SqlConnection(_connectionString);
        await connection.OpenAsync(cancellationToken);
        await using var command = new SqlCommand(sql, connection);

        // Truyền tham số thay vì nối trực tiếp dữ liệu người dùng vào câu SQL.
        command.Parameters.Add("@Carrier", SqlDbType.VarChar, 5).Value =
            string.IsNullOrWhiteSpace(carrier) ? DBNull.Value : carrier.Trim().ToUpperInvariant();
        command.Parameters.Add("@Search", SqlDbType.NVarChar, 100).Value =
            string.IsNullOrWhiteSpace(search) ? DBNull.Value : search.Trim();

        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        while (await reader.ReadAsync(cancellationToken))
        {
            int descriptionIndex = reader.GetOrdinal("MoTa");
            result.Add(new PlanDto(
                reader.GetInt32(reader.GetOrdinal("MaGoi")),
                reader.GetString(reader.GetOrdinal("MaNhaMang")),
                reader.GetString(reader.GetOrdinal("TenNhaMang")),
                reader.GetString(reader.GetOrdinal("MaGoiHienThi")),
                reader.GetString(reader.GetOrdinal("TenGoi")),
                reader.GetDecimal(reader.GetOrdinal("Gia")),
                reader.GetDecimal(reader.GetOrdinal("DungLuongGB")),
                reader.GetString(reader.GetOrdinal("KieuDungLuong")),
                reader.GetInt32(reader.GetOrdinal("ChuKyNgay")),
                reader.IsDBNull(descriptionIndex) ? null : reader.GetString(descriptionIndex),
                reader.GetBoolean(reader.GetOrdinal("PhoBien"))
            ));
        }

        return result;
    }
}
