using Microsoft.Data.SqlClient;

namespace MobileData.Api;

public static class AdminPlanEndpoints
{
    public static void MapAdminPlans(this WebApplication app)
    {
        // Mọi endpoint này đều kiểm tra Bearer token + vai trò tại database.
        app.MapGet("/api/admin/plans", async (HttpRequest request,
            DemoAuthService auth, AdminPlanService plans, CancellationToken ct) =>
        {
            IResult? denied = await CheckPermission(request, auth, ct);
            if (denied is not null) return denied;
            string carrier = request.Query["carrier"].ToString().Trim();
            if (carrier.Length > 5) return Results.BadRequest(new { message = "Mã nhà mạng không hợp lệ." });
            return Results.Ok(await plans.ListAsync(carrier, ct));
        });

        app.MapPost("/api/admin/plans", async (HttpRequest request, NewPlanRequest body,
            DemoAuthService auth, AdminPlanService plans, CancellationToken ct) =>
        {
            IResult? denied = await CheckPermission(request, auth, ct);
            if (denied is not null) return denied;
            string? error = AdminPlanService.Validate(body);
            if (error is not null) return Results.BadRequest(new { message = error });
            try
            {
                int? id = await plans.CreateAsync(body, ct);
                return id is null
                    ? (IResult)Results.BadRequest(new { message = "Nhà mạng không tồn tại hoặc đang ngừng hoạt động." })
                    : Results.Created($"/api/admin/plans/{id}", new { id, message = "Đã thêm gói cước." });
            }
            catch (SqlException ex) when (ex.Number is 2601 or 2627)
            {
                return Results.Conflict(new { message = "Mã gói đã tồn tại ở nhà mạng này." });
            }
        });

        app.MapPut("/api/admin/plans/{id:int}", async (int id, HttpRequest request,
            UpdatePlanRequest body, DemoAuthService auth, AdminPlanService plans,
            CancellationToken ct) =>
        {
            IResult? denied = await CheckPermission(request, auth, ct);
            if (denied is not null) return denied;
            if (id <= 0) return Results.BadRequest(new { message = "ID gói không hợp lệ." });
            string? error = AdminPlanService.Validate(body);
            if (error is not null) return Results.BadRequest(new { message = error });
            bool updated = await plans.UpdateAsync(id, body, ct);
            return updated ? (IResult)Results.Ok(new { message = "Đã cập nhật gói cước." })
                : Results.NotFound(new { message = "Không tìm thấy gói cước." });
        });

        app.MapPatch("/api/admin/plans/{id:int}/availability", async (int id,
            HttpRequest request, PlanSaleStatusRequest body,
            DemoAuthService auth, AdminPlanService plans, CancellationToken ct) =>
        {
            IResult? denied = await CheckPermission(request, auth, ct);
            if (denied is not null) return denied;
            if (id <= 0) return Results.BadRequest(new { message = "ID gói không hợp lệ." });
            bool updated = await plans.SetSaleStatusAsync(id, body.IsForSale, ct);
            return updated ? (IResult)Results.Ok(new { message = body.IsForSale
                    ? "Đã mở bán lại gói cước." : "Đã ngừng bán gói cước." })
                : Results.NotFound(new { message = "Không tìm thấy gói cước." });
        });
    }

    private static async Task<IResult?> CheckPermission(HttpRequest request,
        DemoAuthService auth, CancellationToken ct)
    {
        int status = await auth.CheckAdminAccessAsync(request.Headers.Authorization.ToString(), ct);
        return status switch
        {
            200 => null,
            401 => Results.Json(new { message = "Bạn cần đăng nhập hoặc phiên đã hết hạn." }, statusCode: 401),
            _ => Results.Json(new { message = "Chỉ tài khoản ADMIN mới được thao tác." }, statusCode: 403)
        };
    }
}
