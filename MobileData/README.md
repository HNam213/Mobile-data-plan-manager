#Studen name: Trần Hoài Nam

# MobileData – Android + ASP.NET Core + SQL Server

Dự án đồ án: ứng dụng Android Java/XML quản lý gói cước dữ liệu. Ứng dụng sử dụng ASP.NET Core Minimal API (.NET 10) và SQL Server qua `Microsoft.Data.SqlClient` (ADO.NET). Có USER/ADMIN, OTP **mô phỏng**, đăng ký/hủy gói, số dư nạp tiền **mô phỏng**, quản lý khách hàng, báo cáo thống kê và hộp thư hỗ trợ qua SSE khi ứng dụng đang hoạt động.

> **Chỉ sử dụng trong môi trường học tập/nội bộ.** Không tích hợp SMS OTP, mạng viễn thông hoặc cổng thanh toán thật. Không triển khai API ra Internet trước khi thay thế cơ chế xác thực và bảo vệ dữ liệu.

## Cấu trúc

- `android-app/`: dự án mở trực tiếp trong Android Studio, Java + XML. Đã loại các thư mục cache/build từ ZIP gốc.
- `mobiledata-api/`: mã nguồn Web API, mở/chạy qua .NET SDK 10.
- `database/01_SCHEMA_CHI_CSDL_MOI.sql`: cấu trúc xuất từ SQL Server ngày 23/11/2026, đã bỏ đường dẫn MDF/LDF riêng trên máy xuất. **Chỉ chạy với database mới, không chạy trên CSDL đang có.**
- `database/02_THEM_DU_LIEU_MAU.sql`: thêm bản ghi hoàn toàn tổng hợp vào database `MobileDataDB` **đang tồn tại**, trong transaction; kiểm tra trùng và không chỉnh/xóa dữ liệu cũ.
- `scripts/`: kết nối API tới 2 LDPlayer bằng ADB reverse, khởi động Web API, kiểm tra sức khỏe API.

## Cài đặt lần đầu

1. Cài Android Studio/Android SDK, .NET SDK 10, SQL Server và SQL Server Management Studio.
2. **Sao lưu database hiện có** bằng SSMS trước khi nhập bất kỳ dữ liệu nào.
3. Nếu đã có `MobileDataDB` chạy được, **bỏ qua** `01_SCHEMA_CHI_CSDL_MOI.sql`.
   Nếu cần tạo database trên SQL Server mới, kiểm tra chưa có database `MobileDataDB`, rồi mới chạy script `01` bằng SSMS.
4. Mở `mobiledata-api/appsettings.json`, kiểm tra `ConnectionStrings:MobileDataDB` trỏ tới SQL Server của bạn (mặc định `Server=.\SQLEXPRESS;Database=MobileDataDB;Integrated Security=True;Encrypt=True;TrustServerCertificate=True;`). Không đưa mật khẩu cá nhân vào Git.
5. Mở SSMS, chọn `MobileDataDB`, kiểm tra cấu trúc 10 bảng. Chạy `database/02_THEM_DU_LIEU_MAU.sql` **chỉ khi muốn bổ sung tài khoản và dữ liệu minh họa**. File chỉ chạy một lần, lần sau từ chối để không nhân đôi.
6. Chạy `scripts/CHAY_API.bat`, giữ cửa sổ này mở. Trên PC kiểm tra `http://127.0.0.1:5080/api/health`.
7. Mở **hai LDPlayer** (Multi-instance), bật ADB Debugging – Open local connection. Chạy `scripts/KET_NOI_2_LDPLAYER.bat`; kiểm tra `2/2`. Script nhận ADB từ Android SDK, và thực hiện `reverse tcp:5080 tcp:5080` cho **từng** giả lập.
8. Trong Android Studio mở thư mục `android-app`, chọn **Build APK(s)**. Chạy `scripts/CAI_APP_2_LDPLAYER.bat` để cài APK Debug lên từng máy.
9. Đăng nhập hai tài khoản mẫu riêng biệt để thử yêu cầu hỗ trợ và phản hồi.

### Hai tài khoản sau khi chạy dữ liệu mẫu

| Vai trò | Số điện thoại | Nhà mạng | Ghi chú |
| --- | --- | --- | --- |
| ADMIN | `0398000001` | Viettel (VT) | Đã mở, quản trị hệ thống |
| USER | `0918000002` | VinaPhone (VN) | Đã mở, thuê bao khách hàng |

Hai tài khoản trình diễn này có lịch sử đăng ký nhưng không còn gói ACTIVE, thuận tiện thử đăng ký mới.

Mã OTP **không gửi SMS thật**. API trả OTP để thử nghiệm khi chạy với `ASPNETCORE_ENVIRONMENT=Development`, và Android hiển thị mã theo luồng đã triển khai. Không chạy công khai với thiết lập Development.

### Lưu ý khi demo

- API chỉ nghe trên `localhost:5080`; khi dùng `adb reverse`, mỗi giả lập truy cập `http://127.0.0.1:5080` như đã cấu hình trong `ApiConfig.java`.
- Chạy lại `KET_NOI_2_LDPLAYER.bat` sau khi khởi động lại PC hoặc giả lập vì cấu hình reverse không được lưu vĩnh viễn.
- Hai máy cùng truy cập một SQL Server, **không** đăng nhập cùng một tài khoản nếu muốn kiểm tra thông báo hai chiều.
- SSE có thể nhận sự kiện khi ứng dụng đang chạy; **không có FCM**, nên không bảo đảm thông báo khi app đã đóng hoàn toàn.
- Dữ liệu khách hàng/CCCD và số điện thoại trong SQL mẫu là dữ liệu **tổng hợp**, không dành cho gọi điện hoặc xác minh danh tính.

### Phần chưa kiểm thử tại đây

Gói đã được kiểm tra cấu trúc, liên kết file, XML và đối chiếu tên cột SQL/logic API. Môi trường tạo ZIP không có Windows, Android SDK, SQL Server hoặc .NET SDK để Build/Run cuối cùng. **Trước khi thay dự án đang chạy trên máy, hãy Build Android, `dotnet build`, chạy SQL trong database sao lưu và thử đăng nhập trên hai LDPlayer.**

## Chatbot AI cục bộ (Ollama – bản thử nghiệm đồ án)

Dự án có module trò chuyện với mô hình `qwen2.5:3b` thông qua Ollama chạy trên máy phát triển. Android gọi ASP.NET Core Web API, không kết nối trực tiếp đến Ollama hoặc SQL Server. Phần chatbot tư vấn gói cước, xử lý ngữ cảnh và chuyển hỗ trợ quản trị viên. Giao dịch phải được backend kiểm tra và xác nhận; chatbot không có quyền thực thi SQL tùy ý.

- Cài [Ollama](https://ollama.com/download), sau đó chạy `ollama pull qwen2.5:3b`.
- Khởi động Ollama và SQL Server cục bộ; kiểm tra cấu hình kết nối trong `mobiledata-api/appsettings.json`.
- Khởi động API qua `scripts/CHAY_API.bat` trên Windows hoặc `dotnet run --project mobiledata-api/MobileData.Api.csproj`.
- Mở `android-app/` bằng Android Studio, cấu hình emulator có thể truy cập API máy chủ.

**Lưu ý bảo mật:** Chỉ sử dụng trong môi trường học tập/thử nghiệm. OTP, nạp tiền, giao dịch và thông tin thuê bao là mô phỏng. Không đưa khóa API, mật khẩu, dữ liệu thuê bao thật hoặc database backup vào GitHub. Phải thay cơ chế xác thực demo, cấu hình TLS và kiểm tra phân quyền trước khi triển khai công khai.

**Tình trạng kiểm thử:** Mã nguồn được tổng hợp từ bản gốc người dùng cung cấp và các bản cập nhật chatbot; chưa thực hiện build/kiểm thử tích hợp tại môi trường đóng gói. Bản local đã thử nghiệm của người dùng có thể có thay đổi thêm.
