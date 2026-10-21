-- 반경 검색 쿼리 성능 비교 (MySQL 8.4)
-- 실행: docs/benchmarks/geo/run.ps1  (Docker MySQL 컨테이너 안의 별도 DB geo_bench 에서만 실행합니다)
--
-- 데이터: 100만 행. 절반은 전국(위도 33.0~38.6, 경도 126.0~129.6)에 균등하게,
--         절반은 서울 수도권(위도 37.40~37.70, 경도 126.80~127.20)에 몰아서 만듭니다.
-- 질의:   서울 시청 부근(37.5665, 126.9780) 반경 300m / 1km / 3km, 그리고 인구가 적은 지역(36.0, 128.5) 1km
-- 측정:   각 질의를 반복 실행한 평균 시간(ms). 매 행의 name 을 읽도록 MAX(name)을 함께 조회해서
--         인덱스만 읽고 끝나지 않고 실제 행 조회 비용이 포함됩니다.

DROP DATABASE IF EXISTS geo_bench;
CREATE DATABASE geo_bench;
USE geo_bench;
SET SESSION cte_max_recursion_depth = 1000000;

CREATE TABLE place_bench (
  id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  name      VARCHAR(100) NOT NULL,
  summary   VARCHAR(255) NOT NULL,
  latitude  DOUBLE NOT NULL,
  longitude DOUBLE NOT NULL
) ENGINE=InnoDB;

CREATE TABLE digits (d INT NOT NULL);
INSERT INTO digits VALUES (0),(1),(2),(3),(4),(5),(6),(7),(8),(9);

INSERT INTO place_bench (name, summary, latitude, longitude)
SELECT CONCAT('place-', n), REPEAT('x', 120),
       IF(n % 2 = 0, 33.0 + RAND() * 5.6, 37.40 + RAND() * 0.30),
       IF(n % 2 = 0, 126.0 + RAND() * 3.6, 126.80 + RAND() * 0.40)
FROM (SELECT a.d + b.d*10 + c.d*100 + d.d*1000 + e.d*10000 + f.d*100000 AS n
        FROM digits a, digits b, digits c, digits d, digits e, digits f) nums;

ANALYZE TABLE place_bench;
SELECT COUNT(*) AS total_rows FROM place_bench;

DELIMITER //

-- A) 모든 행에 거리 계산을 적용 (좌표 조건 없음)
CREATE PROCEDURE bench_haversine(IN p_lat DOUBLE, IN p_lng DOUBLE, IN p_r DOUBLE, IN reps INT)
BEGIN
  DECLARE i INT DEFAULT 0; DECLARE n INT; DECLARE m VARCHAR(100); DECLARE t0 DATETIME(6);
  SET t0 = SYSDATE(6);
  WHILE i < reps DO
    SELECT COUNT(*), MAX(name) INTO n, m FROM place_bench
     WHERE 2*6371000*ASIN(SQRT(POWER(SIN(RADIANS(latitude - p_lat)/2),2)
           + COS(RADIANS(p_lat))*COS(RADIANS(latitude))*POWER(SIN(RADIANS(longitude - p_lng)/2),2))) <= p_r;
    SET i = i + 1;
  END WHILE;
  SELECT 'A haversine only' AS variant, p_r AS radius_m, n AS rows_found, reps,
         ROUND(TIMESTAMPDIFF(MICROSECOND, t0, SYSDATE(6)) / 1000 / reps, 3) AS avg_ms;
END //

-- B) 좌표 사각형(BETWEEN)으로 먼저 좁히고, 남은 행에만 거리 계산
CREATE PROCEDURE bench_bbox(IN label VARCHAR(80), IN p_lat DOUBLE, IN p_lng DOUBLE, IN p_r DOUBLE, IN reps INT)
BEGIN
  DECLARE i INT DEFAULT 0; DECLARE n INT; DECLARE m VARCHAR(100); DECLARE t0 DATETIME(6);
  DECLARE d_lat DOUBLE DEFAULT p_r / 111320;
  DECLARE d_lng DOUBLE DEFAULT p_r / (111320 * COS(RADIANS(p_lat)));
  SET t0 = SYSDATE(6);
  WHILE i < reps DO
    SELECT COUNT(*), MAX(name) INTO n, m FROM place_bench
     WHERE latitude  BETWEEN p_lat - d_lat AND p_lat + d_lat
       AND longitude BETWEEN p_lng - d_lng AND p_lng + d_lng
       AND 2*6371000*ASIN(SQRT(POWER(SIN(RADIANS(latitude - p_lat)/2),2)
           + COS(RADIANS(p_lat))*COS(RADIANS(latitude))*POWER(SIN(RADIANS(longitude - p_lng)/2),2))) <= p_r;
    SET i = i + 1;
  END WHILE;
  SELECT label AS variant, p_r AS radius_m, n AS rows_found, reps,
         ROUND(TIMESTAMPDIFF(MICROSECOND, t0, SYSDATE(6)) / 1000 / reps, 3) AS avg_ms;
END //

-- C) POINT 컬럼의 SPATIAL 인덱스(MBRContains)로 좁히고, 남은 행에만 거리 계산
CREATE PROCEDURE bench_spatial(IN p_lat DOUBLE, IN p_lng DOUBLE, IN p_r DOUBLE, IN reps INT)
BEGIN
  DECLARE i INT DEFAULT 0; DECLARE n INT; DECLARE m VARCHAR(100); DECLARE t0 DATETIME(6);
  DECLARE d_lat DOUBLE DEFAULT p_r / 111320;
  DECLARE d_lng DOUBLE DEFAULT p_r / (111320 * COS(RADIANS(p_lat)));
  DECLARE poly VARCHAR(300);
  SET poly = CONCAT('POLYGON((', p_lng - d_lng, ' ', p_lat - d_lat, ',', p_lng + d_lng, ' ', p_lat - d_lat, ',',
                    p_lng + d_lng, ' ', p_lat + d_lat, ',', p_lng - d_lng, ' ', p_lat + d_lat, ',',
                    p_lng - d_lng, ' ', p_lat - d_lat, '))');
  SET t0 = SYSDATE(6);
  WHILE i < reps DO
    SELECT COUNT(*), MAX(name) INTO n, m FROM place_bench
     WHERE MBRContains(ST_GeomFromText(poly, 0), location)
       AND 2*6371000*ASIN(SQRT(POWER(SIN(RADIANS(latitude - p_lat)/2),2)
           + COS(RADIANS(p_lat))*COS(RADIANS(latitude))*POWER(SIN(RADIANS(longitude - p_lng)/2),2))) <= p_r;
    SET i = i + 1;
  END WHILE;
  SELECT 'C spatial index (POINT)' AS variant, p_r AS radius_m, n AS rows_found, reps,
         ROUND(TIMESTAMPDIFF(MICROSECOND, t0, SYSDATE(6)) / 1000 / reps, 3) AS avg_ms;
END //

DELIMITER ;

-- =====================================================================
-- 1단계: 보조 인덱스 없음
-- =====================================================================
SELECT '=== STEP 1: no secondary index ===' AS step;
CALL bench_haversine(37.5665, 126.9780,  300, 5);
CALL bench_haversine(37.5665, 126.9780, 1000, 5);
CALL bench_haversine(37.5665, 126.9780, 3000, 5);
CALL bench_haversine(36.0,    128.5,    1000, 5);
CALL bench_bbox('B0 bbox + haversine, no index', 37.5665, 126.9780, 1000, 5);

SELECT 'EXPLAIN  haversine only, 1km' AS plan;
EXPLAIN SELECT COUNT(*), MAX(name) FROM place_bench
 WHERE 2*6371000*ASIN(SQRT(POWER(SIN(RADIANS(latitude - 37.5665)/2),2)
       + COS(RADIANS(37.5665))*COS(RADIANS(latitude))*POWER(SIN(RADIANS(longitude - 126.978)/2),2))) <= 1000;

-- =====================================================================
-- 2단계: 위도 단일 인덱스
-- =====================================================================
SELECT '=== STEP 2: index (latitude) ===' AS step;
CREATE INDEX idx_lat ON place_bench (latitude);
ANALYZE TABLE place_bench;
CALL bench_bbox('B1 bbox + haversine, idx(latitude)', 37.5665, 126.9780,  300, 50);
CALL bench_bbox('B1 bbox + haversine, idx(latitude)', 37.5665, 126.9780, 1000, 20);
CALL bench_bbox('B1 bbox + haversine, idx(latitude)', 37.5665, 126.9780, 3000, 5);
CALL bench_bbox('B1 bbox + haversine, idx(latitude)', 36.0,    128.5,    1000, 50);

-- =====================================================================
-- 3단계: (위도, 경도) 복합 인덱스  <- 애플리케이션에 적용한 방식
-- =====================================================================
SELECT '=== STEP 3: index (latitude, longitude) ===' AS step;
DROP INDEX idx_lat ON place_bench;
CREATE INDEX idx_lat_lng ON place_bench (latitude, longitude);
ANALYZE TABLE place_bench;
CALL bench_bbox('B2 bbox + haversine, idx(latitude,longitude)', 37.5665, 126.9780,  300, 50);
CALL bench_bbox('B2 bbox + haversine, idx(latitude,longitude)', 37.5665, 126.9780, 1000, 50);
CALL bench_bbox('B2 bbox + haversine, idx(latitude,longitude)', 37.5665, 126.9780, 3000, 50);
CALL bench_bbox('B2 bbox + haversine, idx(latitude,longitude)', 36.0,    128.5,    1000, 50);

SELECT 'EXPLAIN  bbox + haversine, 1km, idx(latitude,longitude)' AS plan;
EXPLAIN SELECT COUNT(*), MAX(name) FROM place_bench
 WHERE latitude BETWEEN 37.5665 - 0.008983 AND 37.5665 + 0.008983
   AND longitude BETWEEN 126.978 - 0.011330 AND 126.978 + 0.011330
   AND 2*6371000*ASIN(SQRT(POWER(SIN(RADIANS(latitude - 37.5665)/2),2)
       + COS(RADIANS(37.5665))*COS(RADIANS(latitude))*POWER(SIN(RADIANS(longitude - 126.978)/2),2))) <= 1000;

SELECT 'EXPLAIN ANALYZE  bbox + haversine, 1km, idx(latitude,longitude)' AS plan;
EXPLAIN ANALYZE SELECT COUNT(*), MAX(name) FROM place_bench
 WHERE latitude BETWEEN 37.5665 - 0.008983 AND 37.5665 + 0.008983
   AND longitude BETWEEN 126.978 - 0.011330 AND 126.978 + 0.011330
   AND 2*6371000*ASIN(SQRT(POWER(SIN(RADIANS(latitude - 37.5665)/2),2)
       + COS(RADIANS(37.5665))*COS(RADIANS(latitude))*POWER(SIN(RADIANS(longitude - 126.978)/2),2))) <= 1000;

-- =====================================================================
-- 4단계: POINT + SPATIAL 인덱스
-- =====================================================================
SELECT '=== STEP 4: SPATIAL index on POINT ===' AS step;
ALTER TABLE place_bench ADD COLUMN location POINT SRID 0 NULL;
UPDATE place_bench SET location = ST_SRID(POINT(longitude, latitude), 0);
ALTER TABLE place_bench MODIFY location POINT SRID 0 NOT NULL;
ALTER TABLE place_bench ADD SPATIAL INDEX sp_location (location);
ANALYZE TABLE place_bench;
CALL bench_spatial(37.5665, 126.9780,  300, 50);
CALL bench_spatial(37.5665, 126.9780, 1000, 50);
CALL bench_spatial(37.5665, 126.9780, 3000, 10);
CALL bench_spatial(36.0,    128.5,    1000, 50);

SELECT 'EXPLAIN  spatial, 1km' AS plan;
EXPLAIN SELECT COUNT(*), MAX(name) FROM place_bench
 WHERE MBRContains(ST_GeomFromText('POLYGON((126.96667 37.557517,126.98933 37.557517,126.98933 37.575483,126.96667 37.575483,126.96667 37.557517))', 0), location)
   AND 2*6371000*ASIN(SQRT(POWER(SIN(RADIANS(latitude - 37.5665)/2),2)
       + COS(RADIANS(37.5665))*COS(RADIANS(latitude))*POWER(SIN(RADIANS(longitude - 126.978)/2),2))) <= 1000;

SELECT 'index sizes (MB)' AS info;
SELECT ROUND(data_length/1024/1024, 1) AS data_mb, ROUND(index_length/1024/1024, 1) AS index_mb
  FROM information_schema.tables WHERE table_schema = 'geo_bench' AND table_name = 'place_bench';

DROP DATABASE geo_bench;
