using System.Data;
using Microsoft.Data.SqlClient;

namespace MobileData.Api;

public sealed record MonthStatDto(int Month, decimal Topup, decimal Spent, int Purchases, int Cancellations);
public sealed record PopularPlanDto(string Code, string Name, int Purchases, decimal Revenue);
public sealed record CarrierStatDto(string Code, string Name, int Subscribers, int ActiveSubscribers,
    decimal TotalTopup, decimal TotalSpent, int Purchases, int Cancellations);
public sealed record StatisticsDto(int Year, string? CarrierCode, decimal TotalTopup, decimal TotalSpent,
    int Purchases, int Cancellations, IReadOnlyList<MonthStatDto> Months,
    IReadOnlyList<PopularPlanDto> PopularPlans, IReadOnlyList<CarrierStatDto> Carriers);

public sealed class MobileDataReportService
{
    private readonly string _connectionString;
    public MobileDataReportService(IConfiguration config) =>
        _connectionString = config.GetConnectionString("MobileDataDB")
            ?? throw new InvalidOperationException("Thiếu chuỗi kết nối MobileDataDB.");

    public async Task<StatisticsDto> ReadAsync(int year, int? subscriberId, string? carrierCode,
        CancellationToken ct)
    {
        // USER chỉ lọc theo ID thuê bao lấy từ access token. Carrier filter chỉ dùng cho ADMIN.
        if (subscriberId is not null) carrierCode = null;

        const string transactionsSql = """
            SELECT MONTH(gd.NgayGiaoDich) AS Thang,
              SUM(CASE WHEN gd.LoaiGiaoDich='NAP_TIEN' AND gd.TrangThai='SUCCESS' THEN gd.SoTien ELSE 0 END),
              SUM(CASE WHEN gd.LoaiGiaoDich='DANG_KY_GOI' AND gd.TrangThai='SUCCESS' THEN gd.SoTien ELSE 0 END),
              SUM(CASE WHEN gd.LoaiGiaoDich='DANG_KY_GOI' AND gd.TrangThai='SUCCESS' THEN 1 ELSE 0 END),
              SUM(CASE WHEN gd.LoaiGiaoDich='HUY_GOI' AND gd.TrangThai='SUCCESS' THEN 1 ELSE 0 END)
            FROM dbo.GiaoDich AS gd
            INNER JOIN dbo.ThueBao AS tb ON tb.MaThueBao = gd.MaThueBao
            WHERE gd.NgayGiaoDich >= DATEFROMPARTS(@Year,1,1)
              AND gd.NgayGiaoDich < DATEADD(YEAR,1,DATEFROMPARTS(@Year,1,1))
              AND (@SubId IS NULL OR gd.MaThueBao = @SubId)
              AND (@Carrier IS NULL OR tb.MaNhaMang = @Carrier)
            GROUP BY MONTH(gd.NgayGiaoDich) ORDER BY Thang;
            """;
        var months = Enumerable.Range(1, 12).Select(m => new MonthStatDto(m,0,0,0,0)).ToArray();
        await using var db = new SqlConnection(_connectionString);
        await db.OpenAsync(ct);
        await using (var cmd = new SqlCommand(transactionsSql, db))
        {
            AddFilters(cmd, year, subscriberId, carrierCode);
            await using var reader = await cmd.ExecuteReaderAsync(ct);
            while (await reader.ReadAsync(ct))
            {
                int month = reader.GetInt32(0);
                months[month - 1] = new MonthStatDto(month,
                    reader.GetDecimal(1), reader.GetDecimal(2), reader.GetInt32(3), reader.GetInt32(4));
            }
        }

        const string popularSql = """
            SELECT TOP (5) gc.MaGoiHienThi, gc.TenGoi,
                   COUNT(*) AS LuotDangKy, SUM(gd.SoTien) AS DoanhThu
            FROM dbo.GiaoDich AS gd
            INNER JOIN dbo.ThueBao AS tb ON tb.MaThueBao = gd.MaThueBao
            INNER JOIN dbo.DangKyGoi AS dk ON dk.MaDangKy = gd.MaDangKy
            INNER JOIN dbo.GoiCuoc AS gc ON gc.MaGoi = dk.MaGoi
            WHERE gd.LoaiGiaoDich='DANG_KY_GOI' AND gd.TrangThai='SUCCESS'
              AND gd.NgayGiaoDich >= DATEFROMPARTS(@Year,1,1)
              AND gd.NgayGiaoDich < DATEADD(YEAR,1,DATEFROMPARTS(@Year,1,1))
              AND (@SubId IS NULL OR gd.MaThueBao = @SubId)
              AND (@Carrier IS NULL OR tb.MaNhaMang = @Carrier)
            GROUP BY gc.MaGoiHienThi, gc.TenGoi
            ORDER BY LuotDangKy DESC, DoanhThu DESC;
            """;
        var popular = new List<PopularPlanDto>();
        await using (var cmd = new SqlCommand(popularSql, db))
        {
            AddFilters(cmd, year, subscriberId, carrierCode);
            await using var reader = await cmd.ExecuteReaderAsync(ct);
            while (await reader.ReadAsync(ct))
                popular.Add(new PopularPlanDto(reader.GetString(0), reader.GetString(1),
                    reader.GetInt32(2), reader.GetDecimal(3)));
        }

        // Bảng phân tích ba nhà mạng chỉ trả về ADMIN, tránh rò rỉ thống kê toàn hệ thống cho USER.
        var carriers = new List<CarrierStatDto>();
        if (subscriberId is null)
        {
            const string carrierSql = """
                SELECT nm.MaNhaMang, nm.TenNhaMang,
                       COUNT(DISTINCT tb.MaThueBao) AS SoThueBao,
                       COUNT(DISTINCT CASE WHEN tb.TrangThai='ACTIVE' THEN tb.MaThueBao END) AS SoDangHoatDong,
                       SUM(CASE WHEN gd.LoaiGiaoDich='NAP_TIEN' AND gd.TrangThai='SUCCESS' THEN gd.SoTien ELSE 0 END),
                       SUM(CASE WHEN gd.LoaiGiaoDich='DANG_KY_GOI' AND gd.TrangThai='SUCCESS' THEN gd.SoTien ELSE 0 END),
                       SUM(CASE WHEN gd.LoaiGiaoDich='DANG_KY_GOI' AND gd.TrangThai='SUCCESS' THEN 1 ELSE 0 END),
                       SUM(CASE WHEN gd.LoaiGiaoDich='HUY_GOI' AND gd.TrangThai='SUCCESS' THEN 1 ELSE 0 END)
                FROM dbo.NhaMang AS nm
                LEFT JOIN dbo.ThueBao AS tb ON tb.MaNhaMang = nm.MaNhaMang
                LEFT JOIN dbo.GiaoDich AS gd ON gd.MaThueBao = tb.MaThueBao
                     AND gd.NgayGiaoDich >= DATEFROMPARTS(@Year,1,1)
                     AND gd.NgayGiaoDich < DATEADD(YEAR,1,DATEFROMPARTS(@Year,1,1))
                GROUP BY nm.MaNhaMang, nm.TenNhaMang
                ORDER BY nm.TenNhaMang;
                """;
            await using var cmd = new SqlCommand(carrierSql, db);
            cmd.Parameters.Add("@Year", SqlDbType.Int).Value = year;
            await using var reader = await cmd.ExecuteReaderAsync(ct);
            while (await reader.ReadAsync(ct))
                carriers.Add(new CarrierStatDto(reader.GetString(0), reader.GetString(1),
                    reader.GetInt32(2), reader.GetInt32(3), reader.GetDecimal(4),
                    reader.GetDecimal(5), reader.GetInt32(6), reader.GetInt32(7)));
        }

        return new StatisticsDto(year, carrierCode, months.Sum(x => x.Topup),
            months.Sum(x => x.Spent), months.Sum(x => x.Purchases),
            months.Sum(x => x.Cancellations), months, popular, carriers);
    }

    private static void AddFilters(SqlCommand cmd, int year, int? subscriberId, string? carrierCode)
    {
        cmd.Parameters.Add("@Year", SqlDbType.Int).Value = year;
        cmd.Parameters.Add("@SubId", SqlDbType.Int).Value = subscriberId is null ? DBNull.Value : subscriberId.Value;
        cmd.Parameters.Add("@Carrier", SqlDbType.VarChar, 5).Value = carrierCode is null ? DBNull.Value : carrierCode;
    }
}
