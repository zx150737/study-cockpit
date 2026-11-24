-- MySQL 官方镜像先创建业务账号，此处收紧为当前应用所需的 DML 权限。
REVOKE ALL PRIVILEGES ON study_cockpit.* FROM 'cockpit'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON study_cockpit.* TO 'cockpit'@'%';
