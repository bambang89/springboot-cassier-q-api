-- Data opsional karyawan: NIK KTP, NIK/nomor induk karyawan, dan foto.
-- Additive & nullable: API versi lama yang belum tahu kolom ini tidak terganggu.
alter table employees add national_id varchar(16) null;
alter table employees add employee_number varchar(50) null;
alter table employees add photo_url nvarchar(500) null;
GO
