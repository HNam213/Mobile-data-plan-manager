using Microsoft.Data.SqlClient;

namespace MobileData.Api;

public static class MobileDataSubscriberEndpoints
{
    private static async Task<IResult?> DenyIfNotAdmin(HttpRequest request,
        DemoAuthService auth, CancellationToken ct)
    {
        int result = await auth.CheckAdminAccessAsync(request.Headers.Authorization.ToString(), ct);
        if (result == 200) return null;
        return Results.Json(new { message = result == 401 ? "Phiên đăng nhập hết hạn." :
            "Chỉ ADMIN mới được quản lý thuê bao." }, statusCode: result);
    }

    public static void MapSubscriberAdmin(this WebApplication app)
    {
        app.MapGet("/api/admin/subscribers", async (HttpRequest request,
            DemoAuthService auth, MobileDataSubscriberService service, CancellationToken ct) =>
        {
            var denied = await DenyIfNotAdmin(request, auth, ct);
            return denied ?? Results.Ok(await service.ListAsync(ct));
        });

        app.MapPost("/api/admin/subscribers", async (HttpRequest request,
            CreateSubscriberRequest input, DemoAuthService auth,
            MobileDataSubscriberService service, CancellationToken ct) =>
        {
            var denied = await DenyIfNotAdmin(request, auth, ct);
            if (denied is not null) return denied;
            string? validation = MobileDataSubscriberService.Validate(input);
            if (validation is not null) return Results.BadRequest(new { message = validation });
            try
            {
                var result = await service.CreateAsync(input, ct);
                return Results.Json(new { message = result.Message, id = result.SubscriberId },
                    statusCode: result.Status);
            }
            catch (SqlException e) when (e.Number is 2601 or 2627)
            {
                return Results.Conflict(new { message = "Số điện thoại hoặc CCCD + nhà mạng đã tồn tại." });
            }
            catch (SqlException e) when (e.Number == 50001)
            {
                return Results.Conflict(new { message = "CCCD đã đạt giới hạn 3 số điện thoại." });
            }
        });

        app.MapPut("/api/admin/subscribers/{id:int}", async (int id, HttpRequest request,
            EditSubscriberRequest input, DemoAuthService auth,
            MobileDataSubscriberService service, CancellationToken ct) =>
        {
            var denied = await DenyIfNotAdmin(request, auth, ct);
            if (denied is not null) return denied;
            if (id <= 0) return Results.BadRequest(new { message = "Mã thuê bao không hợp lệ." });
            string? validation = MobileDataSubscriberService.Validate(input);
            if (validation is not null) return Results.BadRequest(new { message = validation });
            try
            {
                var result = await service.EditAsync(id, input, ct);
                return Results.Json(new { message = result.Message }, statusCode: result.Status);
            }
            catch (SqlException e) when (e.Number is 2601 or 2627)
            {
                return Results.Conflict(new { message = "Số điện thoại đã thuộc thuê bao khác." });
            }
        });
    }
}
