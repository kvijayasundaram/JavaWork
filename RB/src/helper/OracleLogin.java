package helper;

import java.sql.*;
import java.util.Hashtable;

public class OracleLogin {
	class ConnectInfo {
		public String URL;
		public String userName;
		public String password;
		public ConnectInfo(String URL, String userName, String password)
		{
			this.URL =  URL; 
			this.userName = userName; 
			this.password = password;
		}
	}
		
	private Connection con; 
	
    {
		try {
	         Class.forName("oracle.jdbc.driver.OracleDriver");
		} catch( Exception ex) {
			
		}
	}
    	
    Hashtable<String, ConnectInfo> connectStrings;
    Hashtable<String, String> queries;
    
    {
    	connectStrings = new Hashtable<String, ConnectInfo>();
    	connectStrings.put("optordmgr@ccip", new ConnectInfo("jdbc:oracle:thin:@//cvsqopt5:1521/ccip.cablevision.com", "optordmgr", "optordmgr_dev"));
    	connectStrings.put("dstbis@engcup", new ConnectInfo("jdbc:oracle:thin:@//cvsqopt5:1525/engcup.cablevision.com", "dstbis", "dstbis_dev"));
    	connectStrings.put("ams@engcup", new ConnectInfo("jdbc:oracle:thin:@//cvsqopt5:1525/engcup.cablevision.com", "ams", "ams_dev"));
    	connectStrings.put("vowifimgr@vowifip", new ConnectInfo("jdbc:oracle:thin:@//cvsqopt5:1522/itp.cablevision.com", "vowifimgr", "vowifimgr_dev"));
    
    	queries = new Hashtable<String, String>();
    	
    	queries.put("table", "select a.table_name, b.TABLE_NAME parent_table  from user_constraints a, user_constraints b "+
    						 "where a.constraint_type = 'R' and a.table_name = ? and a.R_CONSTRAINT_NAME = b.CONSTRAINT_NAME");
    	queries.put("dml", "select a.table_name, b.TABLE_NAME parent_table  from user_constraints a, user_constraints b "+
				 "where a.constraint_type = 'R' and a.table_name = ? and a.R_CONSTRAINT_NAME = b.CONSTRAINT_NAME");
    	queries.put("code", "select type, name, referenced_owner, referenced_name, referenced_type,  referenced_link_name from USER_DEPENDENCIES "+
    						"where type = 'PACKAGE BODY' and name = 'ORDER_CONF_COMM_SVCS' and referenced_type in ('PROCEDURE', 'PACKAGE', 'FUNCTION', 'TABLE', 'VIEW', 'SEQUENCE')");
    }
    
       
    public void reorder(String[] input, String dbName, String schemaName, String objectType){
    	ConnectInfo cs = connectStrings.get(schemaName+"@"+dbName);
    	try {
    		con=DriverManager.getConnection(cs.URL, cs.userName, cs.password); 
    		Statement stmt=con.createStatement();  
    		ResultSet rs;

    		rs=stmt.executeQuery("select * from emp");  
    		while(rs.next())  
    			System.out.println(rs.getInt(1)+"  "+rs.getString(2)+"  "+rs.getString(3));  
    	}
    	catch(SQLException e){ 
    		System.out.println(e);
    	}  

    }
}
