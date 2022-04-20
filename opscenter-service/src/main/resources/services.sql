-- 删除服务: admin_service
drop service if exists admin_service;

-- 创建服务: admin_service
create service if not exists admin_service (
  login(password varchar) varchar, 
  save(port varchar, allow_others varchar, ssl varchar) varchar,
  admin() varchar,
  start_translate() varchar,
  shutdown() varchar,
  tools(tool_name varchar, args varchar) varchar
)
implement by 'org.lealone.opscenter.service.AdminService'
;

-- 删除服务: ops_service
drop service if exists ops_service;

-- 创建服务: ops_service
create service if not exists ops_service (
  get_languages() varchar,
  get_settings(setting varchar) varchar,
  setting_save(name varchar, driver varchar, url varchar, user varchar) varchar,
  setting_remove(name varchar) varchar,
  read_translations(language varchar) varchar,
  login(url varchar, user varchar, password varchar) varchar,
  logout(jsessionid varchar) varchar,
  test_connection() varchar
)
implement by 'org.lealone.opscenter.service.OpsService'
;

-- 删除服务: query_service
drop service if exists query_service;

-- 创建服务: query_service
create service if not exists query_service (
  query(jsessionid varchar, sql varchar) varchar,
  edit_result(jsessionid varchar, row int, op int, value varchar) varchar
)
implement by 'org.lealone.opscenter.service.QueryService'
;

drop service if exists database_service;

create service if not exists database_service (
  read_all_database_objects(jsessionid varchar) varchar
)
implement by 'org.lealone.opscenter.service.DatabaseService'
;

