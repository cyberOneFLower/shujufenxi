-- 仅在 MySQL 数据目录「首次」初始化时执行；已有数据卷不会重复执行。
-- 若旧卷无 arb 库，请在宿主机执行：
-- docker exec -it arb-mysql-1 mysql -uroot -p'<你的root密码>' -e "CREATE DATABASE IF NOT EXISTS arb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
CREATE DATABASE IF NOT EXISTS arb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
