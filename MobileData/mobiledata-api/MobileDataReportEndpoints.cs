namespace MobileData.Api;

public static class MobileDataReportEndpoints
{
    private static int ParseYear(HttpRequest request)
    {
        string value = request.Query["year"].ToString();
        return value.Length == 0 ? DateTime.Now.Year : int.TryParse(value, out var y) ? y : -1;
    }

    public static void MapMobileDataReports(this WebApplication app)
    {
        app.MapGet("/api/me/stats", async (HttpRequest request, DemoAuthService auth,
            MobileDataReportService reports, CancellationToken ct) =>
        {
            var identity = await auth.GetActiveIdentityAsync(request.Headers.Authorization.ToString(), ct);
            if (identity is null) return Results.Json(new { message = "Phiên đăng nhập không hợp lệ." }, statusCode: 401);
            int year = ParseYear(request);
            if (year < 2000 || year > DateTime.Now.Year + 1)
                return Results.BadRequest(new { message = "Năm không hợp lệ." });
            // USER không được tự chỉ định thuê bao hoặc nhà mạng; ID chỉ lấy từ token đã xác thực.
            return Results.Ok(await reports.ReadAsync(year, identity.SubscriberId, null, ct));
        });

        app.MapGet("/api/admin/stats", async (HttpRequest request, DemoAuthService auth,
            MobileDataReportService reports, CancellationToken ct) =>
        {
            int access = await auth.CheckAdminAccessAsync(request.Headers.Authorization.ToString(), ct);
            if (access != 200)
                return Results.Json(new { message = "Chỉ ADMIN đã đăng nhập mới xem được thống kê hệ thống." },
                    statusCode: access);

            int year = ParseYear(request);
            if (year < 2000 || year > DateTime.Now.Year + 1)
                return Results.BadRequest(new { message = "Năm không hợp lệ." });

            string carrier = request.Query["carrier"].ToString().Trim().ToUpperInvariant();
            if (carrier.Length != 0 && carrier != "VN" && carrier != "VT" && carrier != "MB")
                return Results.BadRequest(new { message = "Nhà mạng không hợp lệ (VN, VT hoặc MB)." });
            return Results.Ok(await reports.ReadAsync(year, null,
                carrier.Length == 0 ? null : carrier, ct));
        });
    }
}
