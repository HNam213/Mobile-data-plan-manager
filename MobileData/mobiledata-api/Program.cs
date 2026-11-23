using Microsoft.Data.SqlClient;
using MobileData.Api;

var builder = WebApplication.CreateBuilder(args);

// Chỉ cho API nghe ở localhost. Bước này kiểm thử trên PC,
// chưa mở API ra mạng LAN hay Internet khi chưa có đăng nhập/phân quyền.
builder.WebHost.UseUrls("http://localhost:5080");
builder.Services.AddProblemDetails();
builder.Services.AddSingleton<MobileDataRepository>();
builder.Services.AddSingleton<DemoAuthService>();
builder.Services.AddSingleton<AdminPlanService>();
builder.Services.AddSingleton<MobileDataUserService>();
builder.Services.AddSingleton<MobileDataSubscriberService>();
builder.Services.AddSingleton<MobileDataReportService>();
builder.Services.AddSingleton<SupportService>();
builder.Services.AddSingleton<OwnerProfileService>();
builder.Services.AddSingleton<SupportRealtimeEvents>();
builder.Services.AddHttpClient<OllamaChatService>();
builder.Services.AddSingleton<ChatConversationMemory>();
builder.Services.AddSingleton<ChatRecommendationState>();
builder.Services.AddSingleton<ChatIntentRouter>();
builder.Services.AddSingleton<ChatActionsService>();

var app = builder.Build();
app.UseExceptionHandler();

// Bổ sung bảng hộp thư hỗ trợ, không ảnh hưởng tám bảng dữ liệu hiện có.
// Tài khoản chạy SQL Server cần quyền tạo bảng trong MobileDataDB (máy phát triển).
try
{
    await app.Services.GetRequiredService<SupportService>().EnsureTablesAsync(CancellationToken.None);
    app.Logger.LogInformation("Đã kiểm tra hai bảng hỗ trợ trong MobileDataDB.");
}
catch (Exception ex)
{
    app.Logger.LogCritical(ex, "Không thể khởi tạo bảng hỗ trợ. Kiểm tra SQL Server và quyền CREATE TABLE.");
    throw;
}

app.MapGet("/", () => Results.Ok(new
{
    app = "MobileData API",
    note = "Đọc dữ liệu công khai và thử nghiệm OTP nội bộ",
    endpoints = new[] { "/api/health", "/api/carriers", "/api/plans", "/api/auth/request-otp", "/api/auth/verify-otp", "/api/auth/me", "/api/auth/logout", "/api/admin/plans" }
}));

// Thử truy cập SQL Server thật; nếu SQL bị tắt sẽ báo 503.
app.MapGet("/api/health", async (MobileDataRepository repository, ILogger<Program> logger,
    CancellationToken cancellationToken) =>
{
    try
    {
        await repository.CheckConnectionAsync(cancellationToken);
        return Results.Ok(new { status = "ok", database = "MobileDataDB" });
    }
    catch (SqlException ex)
    {
        logger.LogError(ex, "Không thể kết nối SQL Server");
        return Results.Problem("Không kết nối được SQL Server. Kiểm tra SQLEXPRESS và MobileDataDB.",
            statusCode: StatusCodes.Status503ServiceUnavailable);
    }
});

app.MapGet("/api/carriers", async (MobileDataRepository repository,
    CancellationToken cancellationToken) =>
    Results.Ok(await repository.GetCarriersAsync(cancellationToken)));

app.MapGet("/api/plans", async (HttpRequest request, MobileDataRepository repository,
    CancellationToken cancellationToken) =>
{
    string carrier = request.Query["carrier"].ToString().Trim();
    string search = request.Query["search"].ToString().Trim();
    if (carrier.Length > 5 || search.Length > 100)
    {
        return Results.BadRequest(new { message = "Từ khóa lọc không hợp lệ." });
    }

    var plans = await repository.GetPlansAsync(carrier, search, cancellationToken);
    return Results.Ok(plans);
});

// Bước 2: đăng nhập OTP mô phỏng và phân quyền lấy từ SQL Server.
app.MapDemoAuth();
app.MapAdminPlans();
app.MapUserOperations();
app.MapSubscriberAdmin();
app.MapMobileDataReports();
app.MapSupportEndpoints();
app.MapOwnerProfiles();
app.MapOllamaChat();

app.Run();
