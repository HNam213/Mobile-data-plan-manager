# Dữ liệu MobileData

- `01_SCHEMA_CHI_CSDL_MOI.sql`: cấu trúc 10 bảng, chỉ dùng khi database hoàn toàn mới. KHÔNG chạy trên CSDL đang có.
- `02_THEM_DU_LIEU_MAU.sql`: bổ sung 30 khách hàng, 40 thuê bao/tài khoản, 36 gói cước, 160 đăng ký, 285 giao dịch, 24 yêu cầu hỗ trợ cùng tin nhắn tương ứng. Không tạo OTP cũ để tránh ảnh hưởng đăng nhập.

Các dòng mẫu được tạo bằng thông tin tổng hợp. CCCD bắt đầu `99990000...`, số điện thoại dùng trong kiểm thử không chứng minh thuê bao có thật. Mục tiêu là tạo lịch sử và báo cáo nhiều tháng.

## Thực hiện an toàn

1. Backup `MobileDataDB` qua SSMS (Tasks > Back Up) **trước** khi chạy script.
2. Không chạy schema trên CSDL đang có.
3. Khi muốn thêm dữ liệu, chạy `02_THEM_DU_LIEU_MAU.sql` trong SSMS và đọc Messages. Script giao dịch toàn phần: nếu có lỗi kiểm tra trùng/khóa ngoại, thay đổi sẽ rollback.
4. Tài khoản minh họa: `0398000001` VT ADMIN, `0918000002` VN USER; cả hai không có gói đang hoạt động để tiện thử mua mới. Yêu cầu OTP trong app.
5. Chạy `SELECT * FROM dbo.NhaMang`, kiểm tra `dbo.KhachHang`, `dbo.GiaoDich` và thống kê qua app.

Chú ý: Không thể xác nhận trạng thái SQL Server thực tế từ file `.sql` đã xuất. Script seed chưa được thực thi trên SQL Server thật trong môi trường này.
