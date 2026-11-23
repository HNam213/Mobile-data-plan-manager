namespace MobileData.Api;

public static class OwnerProfileEndpoints
{
    public static void MapOwnerProfiles(this WebApplication app)
    {
        app.MapGet("/api/me/profile", async (HttpRequest req, DemoAuthService auth,
            OwnerProfileService service, CancellationToken ct) =>
        {
            var identity = await auth.GetActiveIdentityAsync(req.Headers.Authorization.ToString(), ct);
            if (identity is null) return Results.Unauthorized();
            object? profile = await service.GetBySubscriberAsync(identity.SubscriberId, ct);
            return profile is null ? Results.NotFound(new { message = "Khong tim thay ho so." }) : Results.Ok(profile);
        });
        app.MapPut("/api/me/profile", async (HttpRequest req, UpdateOwnerProfileRequest data,
            DemoAuthService auth, OwnerProfileService service, CancellationToken ct) =>
        {
            var identity = await auth.GetActiveIdentityAsync(req.Headers.Authorization.ToString(), ct);
            if (identity is null) return Results.Unauthorized();
            var response = await service.UpdateBySubscriberAsync(identity.SubscriberId, data, ct);
            return Results.Json(new { message = response.Message }, statusCode: response.Status);
        });
        app.MapPost("/api/me/profile/photo", async (HttpRequest req, OwnerPhotoRequest data,
            DemoAuthService auth, OwnerProfileService service, CancellationToken ct) =>
        {
            var identity = await auth.GetActiveIdentityAsync(req.Headers.Authorization.ToString(), ct);
            if (identity is null) return Results.Unauthorized();
            var response = await service.UpdatePhotoAsync(identity.SubscriberId, data, ct);
            return Results.Json(new { message = response.Message }, statusCode: response.Status);
        });
        app.MapGet("/api/admin/customers", async (HttpRequest req, DemoAuthService auth,
            OwnerProfileService svc, CancellationToken ct) =>
        {
            int status = await auth.CheckAdminAccessAsync(req.Headers.Authorization.ToString(), ct);
            return status == 200 ? Results.Ok(await svc.ListCustomersAsync(ct))
                : Results.Json(new { message = "Chi ADMIN duoc xem khach hang." }, statusCode:status);
        });
        app.MapPut("/api/admin/customers/{id:int}", async (int id, HttpRequest req,
            AdminCustomerUpdateRequest data, DemoAuthService auth, OwnerProfileService svc,
            CancellationToken ct) =>
        {
            int status = await auth.CheckAdminAccessAsync(req.Headers.Authorization.ToString(), ct);
            if (status != 200) return Results.Json(new { message="Khong co quyen." }, statusCode:status);
            var answer = await svc.UpdateCustomerAsync(id, data, ct);
            return Results.Json(new { message=answer.Message }, statusCode:answer.Status);
        });
        app.MapDelete("/api/admin/customers/{id:int}", async (int id, HttpRequest req,
            DemoAuthService auth, OwnerProfileService svc, CancellationToken ct) =>
        {
            int status = await auth.CheckAdminAccessAsync(req.Headers.Authorization.ToString(), ct);
            if (status != 200) return Results.Json(new { message="Khong co quyen." }, statusCode:status);
            var answer = await svc.DeleteCustomerAsync(id, ct);
            return Results.Json(new { message=answer.Message }, statusCode:answer.Status);
        });
        app.MapPost("/api/admin/customers/photo", async (HttpRequest req, AdminOwnerPhotoRequest data,
            DemoAuthService auth, OwnerProfileService service, CancellationToken ct) =>
        {
            int status = await auth.CheckAdminAccessAsync(req.Headers.Authorization.ToString(), ct);
            if (status != 200) return Results.Json(new { message = "Chi ADMIN duoc cap nhat anh khach hang." }, statusCode: status);
            var response = await service.UpdateOwnerPhotoByCccdAsync(data, ct);
            return Results.Json(new { message = response.Message }, statusCode: response.Status);
        });
        app.MapPost("/api/admin/customers", async (HttpRequest req, RegisterOwnerRequest data,
            DemoAuthService auth, OwnerProfileService service, CancellationToken ct) =>
        {
            int status = await auth.CheckAdminAccessAsync(req.Headers.Authorization.ToString(), ct);
            if (status != 200) return Results.Json(new { message = "Chi ADMIN duoc tao ho so khach hang." }, statusCode: status);
            var response = await service.RegisterOwnerAsync(data, ct);
            return Results.Json(new { message = response.Message }, statusCode: response.Status);
        });
    }
}
