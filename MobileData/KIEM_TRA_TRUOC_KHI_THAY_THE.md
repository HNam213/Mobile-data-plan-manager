# Kiểm tra trước khi thay thế dự án cũ

**Không xóa bản đang chạy tốt ngay.** Giữ `MobileData(1).zip`, file API cũ và sao lưu `.bak` SQL Server riêng.

- [ ] Mở `android-app` bằng Android Studio, Sync Gradle và Build APK Debug thành công
- [ ] Tại `mobiledata-api`, chạy `dotnet restore` / `dotnet build` thành công
- [ ] Chạy `scripts/CHAY_API.bat` và kiểm tra `http://127.0.0.1:5080/api/health`
- [ ] Sao lưu SQL Server; chỉ chạy script `database/02_THEM_DU_LIEU_MAU.sql` sau khi đối chiếu đúng database
- [ ] Mở 2 LDPlayer, bật ADB debugging, chạy `scripts/KET_NOI_2_LDPLAYER.bat` thấy 2/2
- [ ] Đăng nhập ADMIN 0398000001 (VT) và USER 0918000002 (VN) qua OTP Development
- [ ] Thử mua gói, nạp tiền, hủy gói, lọc khách hàng, thông báo hỗ trợ giữa hai máy
- [ ] Ẩn hoặc thay mọi bí mật SQL trước khi đẩy lên GitHub; đừng đăng dữ liệu thật

Đây là bản làm sạch và ghép nguồn, **không phải** bản đã Build/Run thành công trên máy Windows của bạn.
