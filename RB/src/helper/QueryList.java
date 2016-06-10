package helper;
import java.util.HashMap;
public class QueryList {
	
    public static final HashMap<String, String> queries; 
    static {
    	queries = new HashMap<String, String>();
    	
    	queries.put("constraints", "select distinct lower(a.table_name) NAME, 'table' OBJECT_TYPE, lower(b.owner) as PARENT_OWNER, lower(b.table_name) as PARENT_NAME, 'table' as  PARENT_TYPE, "+
    	                           "null as PARENT_DB_LINK from user_constraints a join all_constraints b on " +
    	                           "(a.R_CONSTRAINT_NAME = b.CONSTRAINT_NAME and a.R_OWNER = b.OWNER ) "+
    							   "where a.constraint_type = 'R' and a.table_name = ?");
    	
    	queries.put("codeDependencies", "select distinct lower(name) as NAME, lower(type) as OBJECT_TYPE, lower(referenced_owner) as PARENT_OWNER, lower(referenced_name) as PARENT_NAME, lower(referenced_type) as PARENT_TYPE,  " +
    							        "lower(referenced_link_name) as PARENT_DB_LINK from user_dependencies where name = ? " +
    			                        "and type in ('PACKAGE', 'PACKAGE BODY', 'PROCEDURE', 'FUNCTION', 'VIEW') " +
    			                        "and referenced_type in ('PACKAGE', 'PACKAGE BODY', 'PROCEDURE', 'FUNCTION', 'VIEW') " +
    							        "and referenced_owner not in ('SYS', 'PUBLIC')");
    	
    	
    	queries.put("synonyms", "select distinct lower(SYNONYM_NAME) as NAME, 'synonym' as OBJECT_TYPE, lower(TABLE_OWNER) as PARENT_OWNER, lower(TABLE_NAME) as PARENT_NAME, 'table' as  PARENT_TYPE, "+
    							"lower((case when instr(db_link, '.') > 0 then substr(db_link, 1, instr(db_link, '.')-1) else db_link end)) as PARENT_DB_LINK from user_synonyms "+
    							"where synonym_name = ?");
    }
    
    public static String getQuery(String name){
    	return queries.get(name);
    }
    
}
