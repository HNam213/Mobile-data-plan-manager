namespace MobileData.Api;

public sealed record AiChatRequest(string? Message);

public static class OllamaChatEndpoints
{
    public static void MapOllamaChat(this WebApplication app)
    {
        app.MapPost("/api/chat/message", async (AiChatRequest? body, HttpRequest request,
            DemoAuthService auth, ChatActionsService chat, ILogger<ChatActionsService> logger,
            CancellationToken ct) =>
        {
            var identity = await auth.GetActiveIdentityAsync(request.Headers.Authorization.ToString(), ct);
            if (identity is null) return Results.Json(new { message = "Vui lòng đăng nhập lại." }, statusCode: 401);
            string? question = body?.Message?.Trim();
            if (string.IsNullOrWhiteSpace(question) || question.Length > 1000)
                return Results.BadRequest(new { message = "Câu hỏi phải từ 1 đến 1000 ký tự." });
            try { return Results.Ok(await chat.MessageAsync(question, identity.SubscriberId, ct)); }
            catch (OperationCanceledException) when (!ct.IsCancellationRequested)
            { return Results.Json(new { message = "AI phản hồi quá lâu. Bạn có thể chuyển quản trị viên.", handoffSuggested = true }, statusCode: 503); }
            catch (Exception ex)
            {
                logger.LogError(ex, "Lỗi chatbot");
                return Results.Json(new { message = "Không thể trả lời lúc này. Bạn có thể chuyển đến quản trị viên.", handoffSuggested = true }, statusCode: 503);
            }
        });
        app.MapPost("/api/chat/reset", async (HttpRequest request, DemoAuthService auth,
            ChatConversationMemory memory, ChatRecommendationState recommendations, CancellationToken ct) =>
        {
            var identity = await auth.GetActiveIdentityAsync(request.Headers.Authorization.ToString(), ct);
            if (identity is null) return Results.Unauthorized();
            memory.Clear(identity.SubscriberId);
            recommendations.Clear(identity.SubscriberId);
            return Results.Ok(new { message = "Đã xóa ngữ cảnh AI của phiên trò chuyện." });
        });
        app.MapPost("/api/chat/prepare", async (ChatActionRequest? body, HttpRequest request,
            DemoAuthService auth, ChatActionsService chat, CancellationToken ct) =>
        {
            var me = await auth.GetActiveIdentityAsync(request.Headers.Authorization.ToString(), ct);
            if (me is null) return Results.Unauthorized();
            if (body is null) return Results.BadRequest(new { message = "Thiếu nội dung yêu cầu." });
            var r = await chat.PrepareAsync(body.Action ?? "", body.TargetId, me.AccountId, me.SubscriberId, ct);
            return Results.Json(r.Body, statusCode: r.Status);
        });
        app.MapPost("/api/chat/confirm", async (ChatConfirmRequest? body, HttpRequest request,
            DemoAuthService auth, ChatActionsService chat, CancellationToken ct) =>
        {
            var me = await auth.GetActiveIdentityAsync(request.Headers.Authorization.ToString(), ct);
            if (me is null) return Results.Unauthorized();
            var r = await chat.ConfirmAsync(body?.ConfirmationId, me.AccountId, me.SubscriberId, ct);
            return Results.Json(r.Body, statusCode: r.Status);
        });
    }
}
