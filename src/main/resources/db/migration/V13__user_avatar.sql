-- Foto profil akun (saat ini hanya bisa diatur akun Kepala Toko).
-- Additive & nullable: API versi lama yang belum tahu kolom ini tidak terganggu.
alter table users add avatar_url nvarchar(500) null;
GO
