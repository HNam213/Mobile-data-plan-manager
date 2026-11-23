-- MOBILEDATA: THIET LAP CSDL MOI TREN SQL SERVER
-- CHI CHAY KHI BAN DA XAC NHAN CSDL MobileDataDB CHUA TON TAI.
-- KHONG CHAY FILE NAY TREN DATABASE DANG CHUA DU LIEU.
-- File nay lay tu schema SQL Server xuat ngay 23/11/2026, bo duong dan MDF/LDF phu thuoc may.
USE [master]
GO
IF DB_ID(N'MobileDataDB') IS NOT NULL
BEGIN
 PRINT N'MobileDataDB da ton tai - BO QUA TOAN BO file schema.';
 SET NOEXEC ON;
END;
GO
CREATE DATABASE [MobileDataDB];
GO
USE [MobileDataDB]
GO
/****** Object:  Table [dbo].[NhaMang]    Script Date: 11/23/2026 11:15:18 AM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[NhaMang](
	[MaNhaMang] [varchar](5) NOT NULL,
	[TenNhaMang] [nvarchar](60) NOT NULL,
	[DangHoatDong] [bit] NOT NULL,
 CONSTRAINT [PK_NhaMang] PRIMARY KEY CLUSTERED 
(
	[MaNhaMang] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY],
 CONSTRAINT [UQ_NhaMang_Ten] UNIQUE NONCLUSTERED 
(
	[TenNhaMang] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[ThueBao]    Script Date: 11/23/2026 11:15:18 AM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[ThueBao](
	[MaThueBao] [int] IDENTITY(1,1) NOT NULL,
	[MaKhachHang] [int] NOT NULL,
	[MaNhaMang] [varchar](5) NOT NULL,
	[SoDienThoai] [varchar](10) NOT NULL,
	[SoDu] [decimal](18, 0) NOT NULL,
	[TrangThai] [varchar](12) NOT NULL,
	[NgayTao] [datetime2](0) NOT NULL,
 CONSTRAINT [PK_ThueBao] PRIMARY KEY CLUSTERED 
(
	[MaThueBao] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY],
 CONSTRAINT [UQ_ThueBao_KhachHang_NhaMang] UNIQUE NONCLUSTERED 
(
	[MaKhachHang] ASC,
	[MaNhaMang] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY],
 CONSTRAINT [UQ_ThueBao_SoDienThoai] UNIQUE NONCLUSTERED 
(
	[SoDienThoai] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[GoiCuoc]    Script Date: 11/23/2026 11:15:18 AM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[GoiCuoc](
	[MaGoi] [int] IDENTITY(1,1) NOT NULL,
	[MaNhaMang] [varchar](5) NOT NULL,
	[MaGoiHienThi] [varchar](20) NOT NULL,
	[TenGoi] [nvarchar](100) NOT NULL,
	[Gia] [decimal](18, 0) NOT NULL,
	[DungLuongGB] [decimal](10, 2) NOT NULL,
	[KieuDungLuong] [varchar](10) NOT NULL,
	[ChuKyNgay] [int] NOT NULL,
	[MoTa] [nvarchar](500) NULL,
	[PhoBien] [bit] NOT NULL,
	[DangBan] [bit] NOT NULL,
	[NgayTao] [datetime2](0) NOT NULL,
 CONSTRAINT [PK_GoiCuoc] PRIMARY KEY CLUSTERED 
(
	[MaGoi] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY],
 CONSTRAINT [UQ_GoiCuoc_MaNhaMang_MaGoi] UNIQUE NONCLUSTERED 
(
	[MaNhaMang] ASC,
	[MaGoiHienThi] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[DangKyGoi]    Script Date: 11/23/2026 11:15:18 AM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[DangKyGoi](
	[MaDangKy] [bigint] IDENTITY(1,1) NOT NULL,
	[MaThueBao] [int] NOT NULL,
	[MaGoi] [int] NOT NULL,
	[GiaLucDangKy] [decimal](18, 0) NOT NULL,
	[DungLuongGBLucDangKy] [decimal](10, 2) NOT NULL,
	[KieuDungLuongLucDangKy] [varchar](10) NOT NULL,
	[ChuKyNgayLucDangKy] [int] NOT NULL,
	[NgayDangKy] [datetime2](0) NOT NULL,
	[NgayHetHan] [datetime2](0) NOT NULL,
	[NgayHuy] [datetime2](0) NULL,
	[TrangThai] [varchar](12) NOT NULL,
 CONSTRAINT [PK_DangKyGoi] PRIMARY KEY CLUSTERED 
(
	[MaDangKy] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  View [dbo].[vGoiDangSuDung]    Script Date: 11/23/2026 11:15:18 AM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO

-- View dùng cho tab 'Gói đang dùng': gói hết hạn/hủy không được hiển thị.
CREATE   VIEW [dbo].[vGoiDangSuDung]
AS
    SELECT
        dk.MaDangKy, dk.MaThueBao, tb.SoDienThoai,
        gc.MaGoiHienThi, gc.TenGoi, nm.TenNhaMang,
        dk.GiaLucDangKy, dk.DungLuongGBLucDangKy,
        dk.KieuDungLuongLucDangKy, dk.ChuKyNgayLucDangKy,
        dk.NgayDangKy, dk.NgayHetHan
    FROM dbo.DangKyGoi AS dk
    JOIN dbo.ThueBao AS tb ON tb.MaThueBao = dk.MaThueBao
    JOIN dbo.GoiCuoc AS gc ON gc.MaGoi = dk.MaGoi
    JOIN dbo.NhaMang AS nm ON nm.MaNhaMang = gc.MaNhaMang
    WHERE dk.TrangThai = 'ACTIVE' AND dk.NgayHetHan > SYSDATETIME();

GO
/****** Object:  Table [dbo].[GiaoDich]    Script Date: 11/23/2026 11:15:18 AM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[GiaoDich](
	[MaGiaoDich] [bigint] IDENTITY(1,1) NOT NULL,
	[MaThueBao] [int] NOT NULL,
	[MaDangKy] [bigint] NULL,
	[LoaiGiaoDich] [varchar](20) NOT NULL,
	[SoTien] [decimal](18, 0) NOT NULL,
	[TrangThai] [varchar](10) NOT NULL,
	[GhiChu] [nvarchar](300) NULL,
	[NgayGiaoDich] [datetime2](0) NOT NULL,
 CONSTRAINT [PK_GiaoDich] PRIMARY KEY CLUSTERED 
(
	[MaGiaoDich] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[KhachHang]    Script Date: 11/23/2026 11:15:18 AM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[KhachHang](
	[MaKhachHang] [int] IDENTITY(1,1) NOT NULL,
	[CCCD] [varchar](12) NOT NULL,
	[HoTen] [nvarchar](100) NOT NULL,
	[NgayTao] [datetime2](0) NOT NULL,
	[GioiTinh] [nvarchar](12) NULL,
	[NamSinh] [smallint] NULL,
	[QueQuan] [nvarchar](120) NULL,
	[DiaChi] [nvarchar](200) NULL,
	[Email] [varchar](150) NULL,
	[AnhDaiDien] [varbinary](max) NULL,
 CONSTRAINT [PK_KhachHang] PRIMARY KEY CLUSTERED 
(
	[MaKhachHang] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY],
 CONSTRAINT [UQ_KhachHang_CCCD] UNIQUE NONCLUSTERED 
(
	[CCCD] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
GO
/****** Object:  Table [dbo].[TaiKhoan]    Script Date: 11/23/2026 11:15:18 AM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[TaiKhoan](
	[MaTaiKhoan] [int] IDENTITY(1,1) NOT NULL,
	[MaThueBao] [int] NOT NULL,
	[VaiTro] [varchar](10) NOT NULL,
	[DangHoatDong] [bit] NOT NULL,
	[NgayTao] [datetime2](0) NOT NULL,
 CONSTRAINT [PK_TaiKhoan] PRIMARY KEY CLUSTERED 
(
	[MaTaiKhoan] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY],
 CONSTRAINT [UQ_TaiKhoan_ThueBao] UNIQUE NONCLUSTERED 
(
	[MaThueBao] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[TinNhanHoTro]    Script Date: 11/23/2026 11:15:18 AM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[TinNhanHoTro](
	[MaTinNhan] [bigint] IDENTITY(1,1) NOT NULL,
	[MaYeuCau] [bigint] NOT NULL,
	[MaTaiKhoan] [int] NOT NULL,
	[VaiTroNguoiGui] [varchar](5) NOT NULL,
	[NoiDung] [nvarchar](2000) NOT NULL,
	[NgayGui] [datetime2](0) NOT NULL,
PRIMARY KEY CLUSTERED 
(
	[MaTinNhan] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[YeuCauHoTro]    Script Date: 11/23/2026 11:15:18 AM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[YeuCauHoTro](
	[MaYeuCau] [bigint] IDENTITY(1,1) NOT NULL,
	[MaThueBao] [int] NOT NULL,
	[TieuDe] [nvarchar](160) NOT NULL,
	[TrangThai] [varchar](12) NOT NULL,
	[NgayTao] [datetime2](0) NOT NULL,
	[NgayCapNhat] [datetime2](0) NOT NULL,
PRIMARY KEY CLUSTERED 
(
	[MaYeuCau] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Table [dbo].[YeuCauOtp]    Script Date: 11/23/2026 11:15:18 AM ******/
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
CREATE TABLE [dbo].[YeuCauOtp](
	[MaYeuCauOtp] [bigint] IDENTITY(1,1) NOT NULL,
	[MaTaiKhoan] [int] NOT NULL,
	[MaOtpHash] [varbinary](32) NOT NULL,
	[SoLanNhapSai] [int] NOT NULL,
	[DaSuDung] [bit] NOT NULL,
	[NgayTao] [datetime2](0) NOT NULL,
	[HetHan] [datetime2](0) NOT NULL,
 CONSTRAINT [PK_YeuCauOtp] PRIMARY KEY CLUSTERED 
(
	[MaYeuCauOtp] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
) ON [PRIMARY]
GO
/****** Object:  Index [IX_DangKyGoi_ThueBao_HetHan]    Script Date: 11/23/2026 11:15:18 AM ******/
CREATE NONCLUSTERED INDEX [IX_DangKyGoi_ThueBao_HetHan] ON [dbo].[DangKyGoi]
(
	[MaThueBao] ASC,
	[NgayHetHan] DESC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
/****** Object:  Index [IX_GiaoDich_ThueBao_Ngay]    Script Date: 11/23/2026 11:15:18 AM ******/
CREATE NONCLUSTERED INDEX [IX_GiaoDich_ThueBao_Ngay] ON [dbo].[GiaoDich]
(
	[MaThueBao] ASC,
	[NgayGiaoDich] DESC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
/****** Object:  Index [IX_HoTro_TinNhan]    Script Date: 11/23/2026 11:15:18 AM ******/
CREATE NONCLUSTERED INDEX [IX_HoTro_TinNhan] ON [dbo].[TinNhanHoTro]
(
	[MaYeuCau] ASC,
	[MaTinNhan] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
/****** Object:  Index [IX_HoTro_ThueBao]    Script Date: 11/23/2026 11:15:18 AM ******/
CREATE NONCLUSTERED INDEX [IX_HoTro_ThueBao] ON [dbo].[YeuCauHoTro]
(
	[MaThueBao] ASC,
	[NgayCapNhat] DESC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
/****** Object:  Index [IX_YeuCauOtp_TaiKhoan_Ngay]    Script Date: 11/23/2026 11:15:18 AM ******/
CREATE NONCLUSTERED INDEX [IX_YeuCauOtp_TaiKhoan_Ngay] ON [dbo].[YeuCauOtp]
(
	[MaTaiKhoan] ASC,
	[NgayTao] DESC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON, OPTIMIZE_FOR_SEQUENTIAL_KEY = OFF) ON [PRIMARY]
GO
ALTER TABLE [dbo].[DangKyGoi] ADD  CONSTRAINT [DF_DangKyGoi_NgayDangKy]  DEFAULT (sysdatetime()) FOR [NgayDangKy]
GO
ALTER TABLE [dbo].[DangKyGoi] ADD  CONSTRAINT [DF_DangKyGoi_TrangThai]  DEFAULT ('ACTIVE') FOR [TrangThai]
GO
ALTER TABLE [dbo].[GiaoDich] ADD  CONSTRAINT [DF_GiaoDich_Ngay]  DEFAULT (sysdatetime()) FOR [NgayGiaoDich]
GO
ALTER TABLE [dbo].[GoiCuoc] ADD  CONSTRAINT [DF_GoiCuoc_PhoBien]  DEFAULT ((0)) FOR [PhoBien]
GO
ALTER TABLE [dbo].[GoiCuoc] ADD  CONSTRAINT [DF_GoiCuoc_DangBan]  DEFAULT ((1)) FOR [DangBan]
GO
ALTER TABLE [dbo].[GoiCuoc] ADD  CONSTRAINT [DF_GoiCuoc_NgayTao]  DEFAULT (sysdatetime()) FOR [NgayTao]
GO
ALTER TABLE [dbo].[KhachHang] ADD  CONSTRAINT [DF_KhachHang_NgayTao]  DEFAULT (sysdatetime()) FOR [NgayTao]
GO
ALTER TABLE [dbo].[NhaMang] ADD  CONSTRAINT [DF_NhaMang_HoatDong]  DEFAULT ((1)) FOR [DangHoatDong]
GO
ALTER TABLE [dbo].[TaiKhoan] ADD  CONSTRAINT [DF_TaiKhoan_VaiTro]  DEFAULT ('USER') FOR [VaiTro]
GO
ALTER TABLE [dbo].[TaiKhoan] ADD  CONSTRAINT [DF_TaiKhoan_HoatDong]  DEFAULT ((1)) FOR [DangHoatDong]
GO
ALTER TABLE [dbo].[TaiKhoan] ADD  CONSTRAINT [DF_TaiKhoan_NgayTao]  DEFAULT (sysdatetime()) FOR [NgayTao]
GO
ALTER TABLE [dbo].[ThueBao] ADD  CONSTRAINT [DF_ThueBao_SoDu]  DEFAULT ((0)) FOR [SoDu]
GO
ALTER TABLE [dbo].[ThueBao] ADD  CONSTRAINT [DF_ThueBao_TrangThai]  DEFAULT ('ACTIVE') FOR [TrangThai]
GO
ALTER TABLE [dbo].[ThueBao] ADD  CONSTRAINT [DF_ThueBao_NgayTao]  DEFAULT (sysdatetime()) FOR [NgayTao]
GO
ALTER TABLE [dbo].[TinNhanHoTro] ADD  DEFAULT (sysdatetime()) FOR [NgayGui]
GO
ALTER TABLE [dbo].[YeuCauHoTro] ADD  DEFAULT ('OPEN') FOR [TrangThai]
GO
ALTER TABLE [dbo].[YeuCauHoTro] ADD  DEFAULT (sysdatetime()) FOR [NgayTao]
GO
ALTER TABLE [dbo].[YeuCauHoTro] ADD  DEFAULT (sysdatetime()) FOR [NgayCapNhat]
GO
ALTER TABLE [dbo].[YeuCauOtp] ADD  CONSTRAINT [DF_YeuCauOtp_LanSai]  DEFAULT ((0)) FOR [SoLanNhapSai]
GO
ALTER TABLE [dbo].[YeuCauOtp] ADD  CONSTRAINT [DF_YeuCauOtp_DaSuDung]  DEFAULT ((0)) FOR [DaSuDung]
GO
ALTER TABLE [dbo].[YeuCauOtp] ADD  CONSTRAINT [DF_YeuCauOtp_NgayTao]  DEFAULT (sysdatetime()) FOR [NgayTao]
GO
ALTER TABLE [dbo].[DangKyGoi]  WITH CHECK ADD  CONSTRAINT [FK_DangKyGoi_GoiCuoc] FOREIGN KEY([MaGoi])
REFERENCES [dbo].[GoiCuoc] ([MaGoi])
GO
ALTER TABLE [dbo].[DangKyGoi] CHECK CONSTRAINT [FK_DangKyGoi_GoiCuoc]
GO
ALTER TABLE [dbo].[DangKyGoi]  WITH CHECK ADD  CONSTRAINT [FK_DangKyGoi_ThueBao] FOREIGN KEY([MaThueBao])
REFERENCES [dbo].[ThueBao] ([MaThueBao])
GO
ALTER TABLE [dbo].[DangKyGoi] CHECK CONSTRAINT [FK_DangKyGoi_ThueBao]
GO
ALTER TABLE [dbo].[GiaoDich]  WITH CHECK ADD  CONSTRAINT [FK_GiaoDich_DangKy] FOREIGN KEY([MaDangKy])
REFERENCES [dbo].[DangKyGoi] ([MaDangKy])
GO
ALTER TABLE [dbo].[GiaoDich] CHECK CONSTRAINT [FK_GiaoDich_DangKy]
GO
ALTER TABLE [dbo].[GiaoDich]  WITH CHECK ADD  CONSTRAINT [FK_GiaoDich_ThueBao] FOREIGN KEY([MaThueBao])
REFERENCES [dbo].[ThueBao] ([MaThueBao])
GO
ALTER TABLE [dbo].[GiaoDich] CHECK CONSTRAINT [FK_GiaoDich_ThueBao]
GO
ALTER TABLE [dbo].[GoiCuoc]  WITH CHECK ADD  CONSTRAINT [FK_GoiCuoc_NhaMang] FOREIGN KEY([MaNhaMang])
REFERENCES [dbo].[NhaMang] ([MaNhaMang])
GO
ALTER TABLE [dbo].[GoiCuoc] CHECK CONSTRAINT [FK_GoiCuoc_NhaMang]
GO
ALTER TABLE [dbo].[TaiKhoan]  WITH CHECK ADD  CONSTRAINT [FK_TaiKhoan_ThueBao] FOREIGN KEY([MaThueBao])
REFERENCES [dbo].[ThueBao] ([MaThueBao])
GO
ALTER TABLE [dbo].[TaiKhoan] CHECK CONSTRAINT [FK_TaiKhoan_ThueBao]
GO
ALTER TABLE [dbo].[ThueBao]  WITH CHECK ADD  CONSTRAINT [FK_ThueBao_KhachHang] FOREIGN KEY([MaKhachHang])
REFERENCES [dbo].[KhachHang] ([MaKhachHang])
GO
ALTER TABLE [dbo].[ThueBao] CHECK CONSTRAINT [FK_ThueBao_KhachHang]
GO
ALTER TABLE [dbo].[ThueBao]  WITH CHECK ADD  CONSTRAINT [FK_ThueBao_NhaMang] FOREIGN KEY([MaNhaMang])
REFERENCES [dbo].[NhaMang] ([MaNhaMang])
GO
ALTER TABLE [dbo].[ThueBao] CHECK CONSTRAINT [FK_ThueBao_NhaMang]
GO
ALTER TABLE [dbo].[TinNhanHoTro]  WITH CHECK ADD FOREIGN KEY([MaTaiKhoan])
REFERENCES [dbo].[TaiKhoan] ([MaTaiKhoan])
GO
ALTER TABLE [dbo].[TinNhanHoTro]  WITH CHECK ADD FOREIGN KEY([MaYeuCau])
REFERENCES [dbo].[YeuCauHoTro] ([MaYeuCau])
GO
ALTER TABLE [dbo].[YeuCauHoTro]  WITH CHECK ADD FOREIGN KEY([MaThueBao])
REFERENCES [dbo].[ThueBao] ([MaThueBao])
GO
ALTER TABLE [dbo].[YeuCauOtp]  WITH CHECK ADD  CONSTRAINT [FK_YeuCauOtp_TaiKhoan] FOREIGN KEY([MaTaiKhoan])
REFERENCES [dbo].[TaiKhoan] ([MaTaiKhoan])
GO
ALTER TABLE [dbo].[YeuCauOtp] CHECK CONSTRAINT [FK_YeuCauOtp_TaiKhoan]
GO
ALTER TABLE [dbo].[DangKyGoi]  WITH CHECK ADD  CONSTRAINT [CK_DangKyGoi_ChuKy] CHECK  (([ChuKyNgayLucDangKy]>(0)))
GO
ALTER TABLE [dbo].[DangKyGoi] CHECK CONSTRAINT [CK_DangKyGoi_ChuKy]
GO
ALTER TABLE [dbo].[DangKyGoi]  WITH CHECK ADD  CONSTRAINT [CK_DangKyGoi_DungLuong] CHECK  (([DungLuongGBLucDangKy]>(0)))
GO
ALTER TABLE [dbo].[DangKyGoi] CHECK CONSTRAINT [CK_DangKyGoi_DungLuong]
GO
ALTER TABLE [dbo].[DangKyGoi]  WITH CHECK ADD  CONSTRAINT [CK_DangKyGoi_Gia] CHECK  (([GiaLucDangKy]>(0)))
GO
ALTER TABLE [dbo].[DangKyGoi] CHECK CONSTRAINT [CK_DangKyGoi_Gia]
GO
ALTER TABLE [dbo].[DangKyGoi]  WITH CHECK ADD  CONSTRAINT [CK_DangKyGoi_Huy] CHECK  (([TrangThai]='CANCELED' AND [NgayHuy] IS NOT NULL OR ([TrangThai]='EXPIRED' OR [TrangThai]='ACTIVE') AND [NgayHuy] IS NULL))
GO
ALTER TABLE [dbo].[DangKyGoi] CHECK CONSTRAINT [CK_DangKyGoi_Huy]
GO
ALTER TABLE [dbo].[DangKyGoi]  WITH CHECK ADD  CONSTRAINT [CK_DangKyGoi_Kieu] CHECK  (([KieuDungLuongLucDangKy]='TOTAL' OR [KieuDungLuongLucDangKy]='DAILY'))
GO
ALTER TABLE [dbo].[DangKyGoi] CHECK CONSTRAINT [CK_DangKyGoi_Kieu]
GO
ALTER TABLE [dbo].[DangKyGoi]  WITH CHECK ADD  CONSTRAINT [CK_DangKyGoi_ThoiGian] CHECK  (([NgayHetHan]>[NgayDangKy] AND ([NgayHuy] IS NULL OR [NgayHuy]>=[NgayDangKy])))
GO
ALTER TABLE [dbo].[DangKyGoi] CHECK CONSTRAINT [CK_DangKyGoi_ThoiGian]
GO
ALTER TABLE [dbo].[DangKyGoi]  WITH CHECK ADD  CONSTRAINT [CK_DangKyGoi_TrangThai] CHECK  (([TrangThai]='EXPIRED' OR [TrangThai]='CANCELED' OR [TrangThai]='ACTIVE'))
GO
ALTER TABLE [dbo].[DangKyGoi] CHECK CONSTRAINT [CK_DangKyGoi_TrangThai]
GO
ALTER TABLE [dbo].[GiaoDich]  WITH CHECK ADD  CONSTRAINT [CK_GiaoDich_DangKy] CHECK  (([LoaiGiaoDich]='NAP_TIEN' AND [MaDangKy] IS NULL OR ([LoaiGiaoDich]='HUY_GOI' OR [LoaiGiaoDich]='DANG_KY_GOI') AND [MaDangKy] IS NOT NULL))
GO
ALTER TABLE [dbo].[GiaoDich] CHECK CONSTRAINT [CK_GiaoDich_DangKy]
GO
ALTER TABLE [dbo].[GiaoDich]  WITH CHECK ADD  CONSTRAINT [CK_GiaoDich_Loai] CHECK  (([LoaiGiaoDich]='HUY_GOI' OR [LoaiGiaoDich]='DANG_KY_GOI' OR [LoaiGiaoDich]='NAP_TIEN'))
GO
ALTER TABLE [dbo].[GiaoDich] CHECK CONSTRAINT [CK_GiaoDich_Loai]
GO
ALTER TABLE [dbo].[GiaoDich]  WITH CHECK ADD  CONSTRAINT [CK_GiaoDich_SoTien] CHECK  (([LoaiGiaoDich]='HUY_GOI' AND [SoTien]=(0) OR ([LoaiGiaoDich]='DANG_KY_GOI' OR [LoaiGiaoDich]='NAP_TIEN') AND [SoTien]>(0)))
GO
ALTER TABLE [dbo].[GiaoDich] CHECK CONSTRAINT [CK_GiaoDich_SoTien]
GO
ALTER TABLE [dbo].[GiaoDich]  WITH CHECK ADD  CONSTRAINT [CK_GiaoDich_TrangThai] CHECK  (([TrangThai]='FAILED' OR [TrangThai]='SUCCESS'))
GO
ALTER TABLE [dbo].[GiaoDich] CHECK CONSTRAINT [CK_GiaoDich_TrangThai]
GO
ALTER TABLE [dbo].[GoiCuoc]  WITH CHECK ADD  CONSTRAINT [CK_GoiCuoc_ChuKyNgay] CHECK  (([ChuKyNgay]>(0)))
GO
ALTER TABLE [dbo].[GoiCuoc] CHECK CONSTRAINT [CK_GoiCuoc_ChuKyNgay]
GO
ALTER TABLE [dbo].[GoiCuoc]  WITH CHECK ADD  CONSTRAINT [CK_GoiCuoc_DungLuong] CHECK  (([DungLuongGB]>(0)))
GO
ALTER TABLE [dbo].[GoiCuoc] CHECK CONSTRAINT [CK_GoiCuoc_DungLuong]
GO
ALTER TABLE [dbo].[GoiCuoc]  WITH CHECK ADD  CONSTRAINT [CK_GoiCuoc_Gia] CHECK  (([Gia]>(0)))
GO
ALTER TABLE [dbo].[GoiCuoc] CHECK CONSTRAINT [CK_GoiCuoc_Gia]
GO
ALTER TABLE [dbo].[GoiCuoc]  WITH CHECK ADD  CONSTRAINT [CK_GoiCuoc_KieuDungLuong] CHECK  (([KieuDungLuong]='TOTAL' OR [KieuDungLuong]='DAILY'))
GO
ALTER TABLE [dbo].[GoiCuoc] CHECK CONSTRAINT [CK_GoiCuoc_KieuDungLuong]
GO
ALTER TABLE [dbo].[KhachHang]  WITH CHECK ADD  CONSTRAINT [CK_KhachHang_CCCD] CHECK  ((len([CCCD])=(12) AND NOT [CCCD] like '%[^0-9]%'))
GO
ALTER TABLE [dbo].[KhachHang] CHECK CONSTRAINT [CK_KhachHang_CCCD]
GO
ALTER TABLE [dbo].[KhachHang]  WITH CHECK ADD  CONSTRAINT [CK_KhachHang_HoTen] CHECK  ((len(ltrim(rtrim([HoTen])))>(0)))
GO
ALTER TABLE [dbo].[KhachHang] CHECK CONSTRAINT [CK_KhachHang_HoTen]
GO
ALTER TABLE [dbo].[TaiKhoan]  WITH CHECK ADD  CONSTRAINT [CK_TaiKhoan_VaiTro] CHECK  (([VaiTro]='ADMIN' OR [VaiTro]='USER'))
GO
ALTER TABLE [dbo].[TaiKhoan] CHECK CONSTRAINT [CK_TaiKhoan_VaiTro]
GO
ALTER TABLE [dbo].[ThueBao]  WITH CHECK ADD  CONSTRAINT [CK_ThueBao_SDT] CHECK  ((len([SoDienThoai])=(10) AND [SoDienThoai] like '0%' AND NOT [SoDienThoai] like '%[^0-9]%'))
GO
ALTER TABLE [dbo].[ThueBao] CHECK CONSTRAINT [CK_ThueBao_SDT]
GO
ALTER TABLE [dbo].[ThueBao]  WITH CHECK ADD  CONSTRAINT [CK_ThueBao_SoDu] CHECK  (([SoDu]>=(0)))
GO
ALTER TABLE [dbo].[ThueBao] CHECK CONSTRAINT [CK_ThueBao_SoDu]
GO
ALTER TABLE [dbo].[ThueBao]  WITH CHECK ADD  CONSTRAINT [CK_ThueBao_TrangThai] CHECK  (([TrangThai]='LOCKED' OR [TrangThai]='ACTIVE'))
GO
ALTER TABLE [dbo].[ThueBao] CHECK CONSTRAINT [CK_ThueBao_TrangThai]
GO
ALTER TABLE [dbo].[TinNhanHoTro]  WITH CHECK ADD CHECK  (([VaiTroNguoiGui]='ADMIN' OR [VaiTroNguoiGui]='USER'))
GO
ALTER TABLE [dbo].[YeuCauHoTro]  WITH CHECK ADD CHECK  (([TrangThai]='CLOSED' OR [TrangThai]='ANSWERED' OR [TrangThai]='OPEN'))
GO
ALTER TABLE [dbo].[YeuCauOtp]  WITH CHECK ADD  CONSTRAINT [CK_YeuCauOtp_HetHan] CHECK  (([HetHan]>[NgayTao]))
GO
ALTER TABLE [dbo].[YeuCauOtp] CHECK CONSTRAINT [CK_YeuCauOtp_HetHan]
GO
ALTER TABLE [dbo].[YeuCauOtp]  WITH CHECK ADD  CONSTRAINT [CK_YeuCauOtp_LanSai] CHECK  (([SoLanNhapSai]>=(0) AND [SoLanNhapSai]<=(5)))
GO
ALTER TABLE [dbo].[YeuCauOtp] CHECK CONSTRAINT [CK_YeuCauOtp_LanSai]
GO
GO
SET NOEXEC OFF;
GO
