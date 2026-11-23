namespace MobileData.Api;

public static class SupportEndpoints
{
    private static async Task<(DemoAuthService.ActiveIdentity? Me, IResult? Error)> IdentityAsync(
        HttpRequest request, DemoAuthService auth, bool adminOnly, CancellationToken ct)
    {
        var identity = await auth.GetActiveIdentityAsync(request.Headers.Authorization.ToString(), ct);
        if (identity is null) return (null, Results.Json(new { message="Phiên đăng nhập đã hết hạn." }, statusCode: 401));
        if (adminOnly && identity.Role != "ADMIN")
            return (null, Results.Json(new { message="Chỉ ADMIN có quyền truy cập." }, statusCode: 403));
        return (identity, null);
    }

    public static void MapSupportEndpoints(this WebApplication app)
    {
        // SSE: ket noi nhan thong bao khi ung dung dang mo, khong dung polling 60 giay.
        app.MapGet("/api/support/events", async (HttpContext ctx, DemoAuthService auth,
            SupportRealtimeEvents events) =>
        {
            var identity = await auth.GetActiveIdentityAsync(ctx.Request.Headers.Authorization.ToString(),
                ctx.RequestAborted);
            if (identity is null) { ctx.Response.StatusCode = 401; return; }
            await events.StreamAsync(ctx, identity.Role == "ADMIN", identity.SubscriberId);
        });

        app.MapGet("/api/me/support", async (HttpRequest req, DemoAuthService auth,
            SupportService svc, CancellationToken ct) =>
        {
            var (me, err) = await IdentityAsync(req, auth, false, ct);
            return err ?? Results.Ok(await svc.ListAsync(me!.SubscriberId, ct));
        });

        // Thông báo phản hồi ADMIN theo thuê bao, không lộ tin nhắn của người khác.
        app.MapGet("/api/me/support/notifications", async (HttpRequest req, DemoAuthService auth,
            SupportService svc, CancellationToken ct) =>
        {
            var (me, err) = await IdentityAsync(req, auth, false, ct);
            if (err is not null) return err;
            string raw = req.Query["after"].ToString();
            if (!long.TryParse(raw, out var after) || after < 0)
                return Results.BadRequest(new { message = "Mốc thông báo không hợp lệ." });
            return Results.Ok(await svc.NewRepliesAsync(me!.SubscriberId, after, ct));
        });

        app.MapPost("/api/me/support", async (HttpRequest req, SupportCreateRequest body,
            DemoAuthService auth, SupportService svc, SupportRealtimeEvents events, CancellationToken ct) =>
        {
            var (me, err) = await IdentityAsync(req, auth, false, ct);
            if (err is not null) return err;
            var result = await svc.CreateAsync(me!.SubscriberId, me.AccountId, body.Title, body.Message, ct);
            if (result.Status == 201)
                events.NotifyAdmins(result.TicketId, body.Title ?? "Yeu cau ho tro", "Co yeu cau ho tro moi.");
            return Results.Json(new { message=result.Message, ticketId=result.TicketId }, statusCode:result.Status);
        });

        app.MapGet("/api/me/support/{id:long}", async (long id, HttpRequest req,
            DemoAuthService auth, SupportService svc, CancellationToken ct) =>
        {
            var (me, err) = await IdentityAsync(req, auth, false, ct);
            if (err is not null) return err;
            var detail = await svc.DetailAsync(id, me!.SubscriberId, ct);
            return detail is null ? Results.NotFound(new { message="Không tìm thấy yêu cầu hỗ trợ." }) : Results.Ok(detail);
        });

        app.MapPost("/api/me/support/{id:long}/reply", async (long id, HttpRequest req,
            SupportReplyRequest body, DemoAuthService auth, SupportService svc,
            SupportRealtimeEvents events, CancellationToken ct) =>
        {
            var (me, err) = await IdentityAsync(req, auth, false, ct);
            if (err is not null) return err;
            var result = await svc.ReplyAsync(id, me!.AccountId, me.SubscriberId, "USER", body.Message, ct);
            if (result.Status == 200) events.NotifyAdmins(id, "Yeu cau ho tro #" + id, "Khach hang vua phan hoi.");
            return Results.Json(new {message=result.Message}, statusCode:result.Status);
        });

        app.MapGet("/api/admin/support", async (HttpRequest req, DemoAuthService auth,
            SupportService svc, CancellationToken ct) =>
        {
            var (me, err) = await IdentityAsync(req, auth, true, ct);
            return err ?? Results.Ok(await svc.ListAsync(null, ct));
        });

        app.MapGet("/api/admin/support/{id:long}", async (long id, HttpRequest req,
            DemoAuthService auth, SupportService svc, CancellationToken ct) =>
        {
            var (me, err) = await IdentityAsync(req, auth, true, ct);
            if (err is not null) return err;
            var detail = await svc.DetailAsync(id, null, ct);
            return detail is null ? Results.NotFound(new { message="Không tìm thấy yêu cầu hỗ trợ." }) : Results.Ok(detail);
        });

        app.MapPost("/api/admin/support/{id:long}/reply", async (long id, HttpRequest req,
            SupportReplyRequest body, DemoAuthService auth, SupportService svc,
            SupportRealtimeEvents events, CancellationToken ct) =>
        {
            var (me, err) = await IdentityAsync(req, auth, true, ct);
            if (err is not null) return err;
            var result = await svc.ReplyAsync(id, me!.AccountId, null, "ADMIN", body.Message, ct);
            if (result.Status == 200)
            {
                int? owner = await svc.GetTicketOwnerAsync(id, ct);
                if (owner is int subId) events.NotifySubscriber(subId, id,
                    "Phan hoi ho tro #" + id, "Quan tri vien da tra loi yeu cau cua ban.");
            }
            return Results.Json(new {message=result.Message}, statusCode:result.Status);
        });

        app.MapPatch("/api/admin/support/{id:long}/status", async (long id, HttpRequest req,
            SupportStatusRequest body, DemoAuthService auth, SupportService svc, CancellationToken ct) =>
        {
            var (me, err) = await IdentityAsync(req, auth, true, ct);
            if (err is not null) return err;
            var result = await svc.ChangeStatusAsync(id, body.Status, ct);
            return Results.Json(new {message=result.Message}, statusCode:result.Status);
        });
    }
}
