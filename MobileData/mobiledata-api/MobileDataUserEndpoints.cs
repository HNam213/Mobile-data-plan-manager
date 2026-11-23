namespace MobileData.Api;

public static class MobileDataUserEndpoints
{
    private static async Task<(DemoAuthService.ActiveIdentity? Identity, IResult? Error)> AuthorizeAsync(
        HttpRequest request, DemoAuthService auth, CancellationToken ct)
    {
        var identity = await auth.GetActiveIdentityAsync(request.Headers.Authorization.ToString(), ct);
        return identity is null
            ? (null, Results.Json(new { message = "Vui lòng đăng nhập lại." }, statusCode: 401))
            : (identity, null);
    }

    public static void MapUserOperations(this WebApplication app)
    {
        app.MapGet("/api/me/balance", async (HttpRequest request, DemoAuthService auth,
            MobileDataUserService service, CancellationToken ct) =>
        {
            var (me, error) = await AuthorizeAsync(request, auth, ct);
            return error ?? Results.Ok(new { balance = await service.BalanceAsync(me!.SubscriberId, ct) });
        });

        app.MapPost("/api/me/topup", async (HttpRequest request, TopupRequest body,
            DemoAuthService auth, MobileDataUserService service, CancellationToken ct) =>
        {
            var (me, error) = await AuthorizeAsync(request, auth, ct);
            if (error is not null) return error;
            var result = await service.TopupAsync(me!.SubscriberId, body.Amount, body.Method, ct);
            return Results.Json(new { message = result.Message, balance = result.Balance }, statusCode: result.Status);
        });

        app.MapPost("/api/me/plans", async (HttpRequest request, BuyPlanRequest body,
            DemoAuthService auth, MobileDataUserService service, CancellationToken ct) =>
        {
            var (me, error) = await AuthorizeAsync(request, auth, ct);
            if (error is not null) return error;
            var result = await service.BuyAsync(me!.SubscriberId, body.PlanId, ct);
            return Results.Json(new { message = result.Message, registrationId = result.RegistrationId,
                balance = result.Balance }, statusCode: result.Status);
        });

        app.MapGet("/api/me/plans", async (HttpRequest request, DemoAuthService auth,
            MobileDataUserService service, CancellationToken ct) =>
        {
            var (me, error) = await AuthorizeAsync(request, auth, ct);
            return error ?? Results.Ok(await service.ActiveAsync(me!.SubscriberId, ct));
        });

        app.MapPost("/api/me/plans/{registrationId:long}/cancel", async (long registrationId,
            HttpRequest request, DemoAuthService auth, MobileDataUserService service, CancellationToken ct) =>
        {
            var (me, error) = await AuthorizeAsync(request, auth, ct);
            if (error is not null) return error;
            var result = await service.CancelAsync(me!.SubscriberId, registrationId, ct);
            return Results.Json(new { message = result.Message }, statusCode: result.Status);
        });

        app.MapGet("/api/me/history", async (HttpRequest request, DemoAuthService auth,
            MobileDataUserService service, CancellationToken ct) =>
        {
            var (me, error) = await AuthorizeAsync(request, auth, ct);
            return error ?? Results.Ok(await service.HistoryAsync(me!.SubscriberId, ct));
        });
    }
}
