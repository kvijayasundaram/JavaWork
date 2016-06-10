package builder;

import helper.Database;
import helper.DbObjectType;
import helper.DbSchema;
import helper.ObjectParent;
import helper.QueryList;
import helper.SQLFileValidator;
import helper.Instruction;
import document.WordDocBuilder;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import document.WordDocBuilder;

import java.util.Comparator;

public class ReleaseNotesBuilder {
	
	public String root;
	public int currentPriority = 0;
	public static Hashtable<String, Integer>objectRanking;
	public static Hashtable<String, Integer> constraintRanking;
	public static Hashtable<String, Integer>backoutRanking;
    public static Hashtable<String, String> prefixes;
    public static Hashtable<String, String> objectTypes;
    public static Hashtable<String, String> objectsyntaxes;
    public boolean isCommitRequired;
    public boolean isConnPoolRefreshRequired;
	public Vector<String> synonymBackouts;
	public Vector<String> sequenceBackouts;
	public Vector<String> codeBackouts;
	private long startDate = 0;
	private Properties dbProperties;
	
	public String trunkPath = "C:\\svn\\oracle\\database\\trunk";
	
	String[] objectPriority = {"role","user","dblink", "table", "constraint", "object",  "collection",  "sequence",  "synonym", "view", "index", "function",  "procedure",  "package", "package body",  "privilege",  "trigger",  "scheduler", "job", "dml", "report"};
	
	
	//private List<Instruction> instructions = new ArrayList<Instruction>();
    private List<Database> databases = new ArrayList<Database>();
    private List<Database> exception = new ArrayList<Database>();
    private List<Database> incrementalDatabases = new ArrayList<Database>();
	private int dbId = 0;
	private int schemaId = 0;
	private int objectTypeId = 0;
	
	static {
		constraintRanking = new Hashtable<String, Integer>();
		constraintRanking.put("not null", 1);
		constraintRanking.put("primary", 2);
		constraintRanking.put("unique", 3);
		constraintRanking.put("check", 4);
		constraintRanking.put("foreign", 5);
		//-------------------
		objectRanking = new Hashtable<String, Integer>();
		objectRanking.put("role", 		1);
		objectRanking.put("user", 		2);
		objectRanking.put("dblink", 	3);
		objectRanking.put("table", 		4);
		objectRanking.put("object", 	5);
		objectRanking.put("collection", 6);
		objectRanking.put("sequence", 	7);
		objectRanking.put("synonym", 	8);
		objectRanking.put("view", 		9);
		objectRanking.put("function", 	10);
		objectRanking.put("procedure", 	11);
		objectRanking.put("context", 	12);
		objectRanking.put("package", 	13);
		objectRanking.put("constraint", 14);
		objectRanking.put("index", 		15);
		objectRanking.put("privilege", 	16);
		objectRanking.put("trigger", 	17);
		objectRanking.put("scheduler", 	18);
		objectRanking.put("dml", 		19);
		objectRanking.put("report", 	20);
		//-------------------
		backoutRanking = new Hashtable<String, Integer>();
		backoutRanking.put("role", 			1);
		backoutRanking.put("user", 			2);
		backoutRanking.put("dblink", 		3);
		backoutRanking.put("index", 		4);
		backoutRanking.put("constraint", 	5);
		backoutRanking.put("collection", 	6);
		backoutRanking.put("object", 		7);
		backoutRanking.put("sequence", 		8);
		backoutRanking.put("synonym", 		9);
		backoutRanking.put("view", 			10);
		backoutRanking.put("table", 		11);
		backoutRanking.put("function", 		12);
		backoutRanking.put("procedure", 	13);
		backoutRanking.put("package", 		14);
		backoutRanking.put("privilege", 	15);
		backoutRanking.put("trigger", 		16);
		backoutRanking.put("context", 		17);
		backoutRanking.put("scheduler", 	18);
		backoutRanking.put("dml", 			19);
		backoutRanking.put("report", 		20);
		//-------------------
		prefixes = new Hashtable<String,String>();
		prefixes.put("role", "role");
		prefixes.put("user", "user");
		prefixes.put("dblink", "dblink");
		prefixes.put("tab", "table");
		prefixes.put("cons", "constraint");
		prefixes.put("idx", "index");
		prefixes.put("obj", "object");
		prefixes.put("coll", "collection");
		prefixes.put("seq", "sequence");
		prefixes.put("syn", "synonym");
		prefixes.put("fnc", "function");
		prefixes.put("prc", "procedure");
		prefixes.put("pkb", "package");
		prefixes.put("pks", "package");
		prefixes.put("view", "view");
		prefixes.put("job", "scheduler");
		prefixes.put("priv", "privilege");
		prefixes.put("dml", "dml");
		prefixes.put("rpt", "report");
		prefixes.put("trg", "trigger");
		//-------------------
		objectTypes = new Hashtable<String,String>();
		objectTypes.put("table", "tab");
		//-------------------
		objectsyntaxes = new Hashtable<String, String>();
		objectsyntaxes.put("tab", "(CREATE|ALTER|DROP)\\s+TABLE\\s+<NAME>\\s*\\(.+\\)\\s*;");
		objectsyntaxes.put("idx", "(CREATE|DROP)\\s+(UNIQUE\\s+)?INDEX\\s+<NAME>\\s+ON\\s+<PARENTNAME>\\s*\\(.+\\)\\s*;");
		objectsyntaxes.put("cons", "ALTER\\s+TABLE\\s+<PARENTNAME>\\s+(ADD|DROP)\\s+CONSTRAINT\\s+<NAME>\\s+(CHECK|PRIMARY\\s+KEY|FOREIGN\\s+KEY)\\s*\\(.+\\)\\s*;");
		objectsyntaxes.put("syn", "(CREATE|DROP)\\s+SYNONYM\\s+<NAME>\\s+FOR\\s+.+;");
		objectsyntaxes.put("seq", "(CREATE|DROP)\\s+SEQUENCE\\s+<NAME>\\s+.+;");
		objectsyntaxes.put("priv", "(GRANT|REVOKE)\\s+(SELECT|INSERT|UPDATE|DELETE|EXECUTE|ALL){1}(\\s*,\\s*SELECT|\\s*,\\s*+UPDATE|\\s*,\\s*+INSERT|\\s*,\\s*DELETE)*\\s+ON\\s+<NAME>\\s+(TO|FROM)\\s+\\S+\\s*;");
		
		objectsyntaxes.put("fnc", "(CREATE(\\s+OR\\s+REPLACE)?|DROP)\\s+FUNCTION\\s+(<PARENTNAME>\\.)?<NAME>(\\s|\\().+;\\s+\\/$");
		objectsyntaxes.put("prc", "(CREATE(\\s+OR\\s+REPLACE)?|DROP)\\s+PROCEDURE\\s+(<PARENTNAME>\\.)?<NAME>(\\s|\\().+;\\s+\\/$");
		objectsyntaxes.put("pks", "(CREATE(\\s+OR\\s+REPLACE)?|DROP)\\s+PACKAGE\\s+(<PARENTNAME>\\.)?<NAME>\\s.+;\\s+\\/$");
		objectsyntaxes.put("pkb", "(CREATE(\\s+OR\\s+REPLACE)?|DROP)\\s+PACKAGE\\s+BODY\\s+(<PARENTNAME>\\.)?<NAME>\\s.+;\\s+\\/$");
		objectsyntaxes.put("trg", "(CREATE(\\s+OR\\s+REPLACE)?|DROP)\\s+TRIGGER\\s+(<PARENTNAME>\\.)?<NAME>\\s.+;\\s+\\/$");
		objectsyntaxes.put("view", "(CREATE(\\s+OR\\s+REPLACE)?|DROP)\\s+VIEW\\s+(<PARENTNAME>\\.)?<NAME>\\s+AS.+(;|\\/)$");
		objectsyntaxes.put("obj", "(CREATE|DROP)\\s+TYPE\\s+(<PARENTNAME>\\.)?<NAME>\\s+(AS|IS).+;\\s+\\/$");
		objectsyntaxes.put("coll", "(CREATE|DROP)\\s+TYPE\\s+(<PARENTNAME>\\.)?<NAME>\\s+(AS|IS).+;\\s+\\/$");
		objectsyntaxes.put("dblink", "CREATE\\s+DATABASE\\s+LINK\\s+<NAME>\\s+CONNECT\\s+TO\\s+<PARENTNAME>\\s+IDENTIFIED\\s+BY\\s+\\S+\\s+USING\\s+\\S+(\\s*;|\\s+\\/)$");
		objectsyntaxes.put("dml", "(INSERT|UPDATE|DELETE)");		
		objectsyntaxes.put("job", "BEGIN\\s+.*(DBMS_SCHEDULER|DBMS_JOB)");
		objectsyntaxes.put("role", "(CREATE|ALTER|DROP)\\s+.*ROLE");
		objectsyntaxes.put("user", "(CREATE|ALTER|DROP)\\s+.*USER");
	}
	

	//---------------------------------------------------
	private synchronized int getNextDbId(){	dbId = dbId + Database.dbSeqInterval;	return dbId;	}
	private synchronized int getNextSchemaId(){ schemaId = schemaId + DbSchema.schemaSeqInterval ; return schemaId; }
	private synchronized int resetSchemaId() { schemaId = 0; return schemaId;	}
	private synchronized int getNextObjectTypeId(){	objectTypeId = DbObjectType.objectTypeSeqInterval + objectTypeId; return objectTypeId;	}
	private synchronized int resetObjectTypeId() { objectTypeId = 0; return objectTypeId;	}
	//-----------------------------------------------------------
	public long getStartDate() {
		return startDate;
	}
	
	public void setStartDate(long startDate) {
		this.startDate = startDate;
	}

	public ReleaseNotesBuilder() throws SQLException {
        
		dbProperties = new Properties();
		try {
			dbProperties.load(this.getClass().getResourceAsStream("dbConnection.properties"));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		//Class.forName("com.mysql.jdbc.Driver");
		DriverManager.registerDriver (new oracle.jdbc.OracleDriver());
		//----------------
		
	}
	
	private final boolean isDbValid(String text){
		String validDatabases = (String) dbProperties.get("validDatabases");
		return validDatabases.contains(text);
	}
	
	public String writeContents(File file, String prefix, String currentLevel, Database database, DbSchema dbSchema, DbObjectType dbObjectType){
		StringBuffer text = new StringBuffer("");
		File[] contents;
		
		if (currentLevel.equalsIgnoreCase("PROJECT")){
			if(file.isDirectory()){
				contents = file.listFiles();
				if (contents != null && contents.length > 0){
					for(File content : contents){
						// ignore the build directory
						if (!content.getName().equalsIgnoreCase("build"))  {
							if (isDbValid(content.getName()))  {
								database = new Database(databases, content.getName(), getNextDbId());
								resetSchemaId();
								text.append(writeContents(content, "@", "DATABASE", database, dbSchema, dbObjectType )); 
								databases.add(database); // create the database as they are created.
							}
							else
							{
								return "Error: Project folder contains unsupported database names\n";
							}
						}
					}
				}
				else{
					return "Error: Project folder is empty\n";
				}
			}
			else{
				return "Error: Project folder not found\n";
			}
		}
		else if(currentLevel.equalsIgnoreCase("DATABASE")){
			if(file.isDirectory()){
				contents = file.listFiles();
				if (contents != null && contents.length > 0){
					for(File content : contents){
						dbSchema = new DbSchema(database, database.dbName, content.getName(), getNextSchemaId(), "connect " + content.getName() + "@" + database.dbName);
						text.append(writeContents(content, prefix + file.getName(), "SCHEMA", database, dbSchema, dbObjectType)); 
						dbSchema.populateOtherinstructions();
						database.addSchema(dbSchema);
					}
				}
				else{
					return "Error: Database folder " + file.getName() + " is empty\n";
				}
			}
			else{
				return "Error: Database folder was found to be a file\n";
			}
		}
		else if(currentLevel.equalsIgnoreCase("SCHEMA")){
			if(file.isDirectory()){
				contents = file.listFiles();
				if (contents != null && contents.length > 0){
					if(file.getName().equals("system"))
					{
						dbSchema.schemaSequence = 0; // always give priority for system level instructions.
					}
					resetObjectTypeId();
					text.append("Connect " + file.getName() + "@" + file.getParentFile().getName()+"\n");
					for (String objectType : objectPriority) {
						for(File content : contents){
							if(objectType.equalsIgnoreCase(content.getName())) {
							text.append(writeContents(content, prefix + "/" + file.getName(), "OBJECT", database, dbSchema, dbObjectType)); 
						    break;
							}
						}
					}
				}
				else{
					return "Error: DB Schema folder " + file.getName() + " is empty\n";
				}
			}
			else{
				return "Error: Database schema folder was found to be a file\n";
			}
		}
		else if(currentLevel.equalsIgnoreCase("OBJECT")){
			if(file.isDirectory()){
				contents = file.listFiles();
				if (contents != null && contents.length > 0){
					dbObjectType = new DbObjectType(dbSchema, database.dbName, dbSchema.schemaName,file.getName(), getNextObjectTypeId());
					for(File content : contents){
						text.append(writeContents(content, prefix + "/" + file.getName(), "ITEM", database, dbSchema, dbObjectType));
					}
					dbSchema.addObjectType(dbObjectType);
				}
				else{
					return "Error: object folder " + file.getName() + " is empty\n";
				}
			}
			else{
				return "Error: Database object folder was found to be a file\n";
			}
		}
		else if(currentLevel.equalsIgnoreCase("ITEM")){
			if(!file.isDirectory()){
				if (startDate == 0 || startDate < file.lastModified()) {
					if (validateFile(file, file.getParentFile().getName(), database, dbSchema, dbObjectType)){
						text.append(prefix + "/" + file.getName() + "\n");
					}
					else{
						text.append(prefix + "/" + file.getName() + "\n");
						text.append("Error: item " + file.getName() + " has content issues. Please check\n");
					}
				}
			}
			else{
				return "Error: folders are not allowed in items list\n";
			}
		}
		return text.toString();
	}
	
	private boolean validateFile(File file, String parent, Database database, DbSchema dbSchema, DbObjectType dbObjectType) {
		// TODO Auto-generated method stub
		String fileName = file.getName();
		String[] fileNameComponents = fileName.split("\\.");
		String prefix, suffix;
		String parentName = null;
		String objectName = null;
		String parentObjectType = "table";
		boolean exists = false;
		boolean parentExists = false;
		boolean isOK = false;
		String errorMessage = null;
		Instruction instruction;
		StringBuilder comments = new StringBuilder("");
		StringBuilder body = new StringBuilder("");
		
		if (fileNameComponents == null || fileNameComponents.length < 3)
		   isOK = false;
		else
		{
			prefix = fileNameComponents[0];
			parentName = fileNameComponents[1];
			objectName = fileNameComponents[fileNameComponents.length-2];
			suffix = fileNameComponents[fileNameComponents.length-1];
			if (prefixes.get(prefix) == null || !parent.equals(prefixes.get(prefix))){
				errorMessage = ("ERROR:The file prefix " + prefix + " does not match parent name " + parent);
				isOK = false;
			}
			else if (!suffix.equals("sql")) {
				errorMessage = ("ERROR:The file suffix " + suffix + " is not .sql");
				isOK = false;
			}
			else if (nameHasWhitespaces(fileNameComponents)) {
				errorMessage = ("ERROR:The file name " + fileName + " has whitespaces");
				isOK = false;
			}
			else{
				try {
					isOK = checkFileContent(file, fileNameComponents, dbSchema.schemaName, comments, body);
				} catch(IOException ex){
					errorMessage = ("ERROR:The file " + file.getPath() + " caused IO exception while reading");
				}
				isOK = true;
			}
		}

		instruction = new Instruction(
				dbObjectType, 0, 
				"@"+ database.dbName + "/"+ dbSchema.schemaName+"/"+ dbObjectType.objectTypeName+"/"+ fileName,  
				database.dbName, dbSchema.schemaName, dbObjectType.objectTypeName, objectName,  
	    		null, fileName, isOK, errorMessage,  body.toString()
	    );

		exists = isFoundInTrunk("\\" + database.dbName + "\\" + dbSchema.schemaName + "\\" + dbObjectType.objectTypeName , fileName, dbObjectType.objectTypeName);
		
		if (dbObjectType.objectTypeName.equals("constraint") || 
			dbObjectType.objectTypeName.equals("index") || 
			dbObjectType.objectTypeName.equals("trigger") || 
			dbObjectType.objectTypeName.equals("dml") ) 
		{
		    parentExists = isFoundInTrunk("\\" + database.dbName + "\\" + dbSchema.schemaName + "\\" + parentObjectType, parentName, parentObjectType);
		}
		else if(dbObjectType.objectTypeName.equals("user") || 
				dbObjectType.objectTypeName.equals("role"))  {
			parentExists = true;
		}
		else{
			parentExists = isSchemaFoundInTrunk("\\" + database.dbName + "\\" + dbSchema.schemaName );
		}
		//System.out.println("@@@ comment for:" + fileName + ":" + comments.toString());
		instruction.createOtherInfo(exists, parentExists, parentName, comments.toString());
		dbObjectType.addInstruction(instruction);
		return isOK;
	}

	private boolean nameHasWhitespaces(String[] fileNameComponents) {
		// TODO Auto-generated method stub
		for (String s : fileNameComponents){
			for (char c : s.toCharArray()) {
			    if (Character.isWhitespace(c)) {
			       return true;
			    }
			}
		}
		return false;
	}
	
	public boolean checkFileContent(File file, String[] fileNameComponents, String schemaName, StringBuilder comments, StringBuilder body) throws IOException{
		String objectType = fileNameComponents[0];
		String objectName = fileNameComponents[1];
		String parentObjectName = fileNameComponents[1];
		boolean result = true;
						
		if (objectType.equals("cons") ||
		    objectType.equals("idx") ||
			objectType.equals("trg")
			)
		{
			if (fileNameComponents.length != 4) {
				System.out.println("ERROR: in naming convention for file:" + file.getPath());
				result = false;
			}
			else 
				objectName = fileNameComponents[2];
		}
		else if(objectType.equals("dblink")) {
			if (fileNameComponents.length < 4) {
				System.out.println("ERROR: in naming convention for file:" + file.getPath());
				result = false;
			}
			else
				objectName = fileNameComponents[2];
		}
		else {
			if (fileNameComponents.length < 3) 
			{
				System.out.println("ERROR: in naming convention for file:" + file.getPath());
				result = false;
			}
			else
				parentObjectName = schemaName;
		}

		result = regexpCheck(file, objectType, objectName, parentObjectName, comments, body);
		return result;
	}
	
	public String generate(String mainPath){
		root = mainPath;
		String text = "";
		File file = new File(mainPath);
		if (file.isDirectory()) {	
				text = writeContents(file, file.getName(), "PROJECT", null, null, null);
		}
		else{
			text = "Error: project main folder:" + file.getName() + " is not a directory";
		}
		return text;
	}

	public static void main(String[] args) throws IOException, InterruptedException, SQLException{
		ReleaseNotesBuilder rb = new ReleaseNotesBuilder();
		//rb.test(args[0]);
		if (args.length > 1){
			SimpleDateFormat f = new SimpleDateFormat("MM/dd/yyyy@H:m:s");
			Date d;
			try {
				d = f.parse(args[1]);
				System.out.println("Getting all file changed since:" + d.toGMTString());
				rb.setStartDate(d.getTime());
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				System.out.println("The start Date parameter must be of format mm/dd/yyyy hh24:mi:ss");
				e.printStackTrace();
			}
		}
		System.out.print(rb.generate(args[0]));
		System.out.print("--------------------------****FINDING DEPENDENCIES****------------------\n");
		rb.resequenceList();
		System.out.print("--------------------------****DEPLOYMENT****------------------------\n");
		//
		//rb.instructions.stream().sorted(Comparator.comparing((item)->item.rank)).forEach((item)->{System.out.println(item.text);});
		rb.databases.stream().sorted(Comparator.comparing((item)->item.dbSequence)).forEach(item->item.print(Instruction.PrintType.DEPLOYMENT));
		System.out.print("--------------------------****BACKINGOUT****------------------------\n");
		rb.databases.stream().sorted(Comparator.comparing((item)->item.dbSequence)).forEach(item->item.print(Instruction.PrintType.BACKOUT));
		System.out.print("----------------------------------------------------------\n");	
		
		//------------ kvs testing only --------------
		//WordDocBuilder  wb = new WordDocBuilder(args[0]);
		//wb.write(rb.databases);
		//wb.writeToFile();
	}
	
	public boolean regexpCheck(File file, String objectType, String objectName, 
			String parentObjectName, StringBuilder comments, StringBuilder body) throws IOException {
		
		SQLFileValidator sf = new SQLFileValidator();
		sf.parse(file, objectType);
		String codeText = sf.getContentText();
		comments.append(sf.getCommentText());
		body.append(codeText);
		String patternString;
		//System.out.println(objectName + ":" + parentObjectName + ":" + objectType);
		try {
		    patternString = objectsyntaxes.get(objectType).replaceAll("<NAME>", Matcher.quoteReplacement(objectName.toUpperCase())).replaceAll("<PARENTNAME>", Matcher.quoteReplacement(parentObjectName.toUpperCase()));
		    //System.out.println("*******PatternString:" + patternString.toString());
		}
		catch(IllegalArgumentException ex) {
			System.out.println("Error:IllegalException on:" + objectName + ":" + parentObjectName + ":" + objectType);
			return true;
		}
		//System.out.println("patternString:" + patternString);
		Pattern p = Pattern.compile(patternString, Pattern.DOTALL);
		Matcher m = p.matcher(codeText);
		return m.find();
	}
	
	public boolean isFoundInTrunk(String context, String fileName, String objectType){
		boolean result = false;
		Path path;
        String prefix;
		if(fileName.endsWith(".sql")){
			path = Paths.get(trunkPath+"\\"+context +"\\"+fileName);
		    result = Files.exists(path);
		}
		else {
			prefix = objectTypes.get(objectType);
			path = Paths.get(trunkPath+"\\"+context +"\\"+prefix+"."+fileName+".sql");
		    result = Files.exists(path);
		}
		/*
		if (result)
			System.out.println("@@@" + fileName + "@@@ exists @@@");
	   */
		return result;
	}
	
	public boolean isSchemaFoundInTrunk(String context){
		boolean result = false;
		Path path = Paths.get(trunkPath+"\\"+context );
		result = Files.exists(path);
		return result;
	}
	
	private void reorderDBSchemas(Instruction ins, String type){
		int dbMaxSeq;
		int schemaMaxSeq;
		
		/*
		dbMaxSeq = dbSchemas.stream().map(item->item.dbSequence).max(Comparator.comparing(item->item.intValue())).get();

		if (type.equals("db")) {
			for (DbSchema item: dbSchemas){
				if (item.dbName.equals(ins.dbName))
					item.dbSequence = dbMaxSeq + 1; // send it backwards.
			}
		}
		else {
			schemaMaxSeq = dbSchemas.stream().filter(item->item.dbName.equals(ins.dbName)).map(item->item.schemaSequence).max(Comparator.comparing(item->item.intValue())).get();
			for (DbSchema item: dbSchemas.stream().filter(item->item.dbName.equals(ins.dbName)).collect(Collectors.toList())){
				if (item.schemaName.equals(ins.schemaName))
					item.schemaSequence = schemaMaxSeq + 1; // send it backwards.
			}
		}
		*/
	}
	
	public List<ObjectParent> findDependencies(String dbName, String schema, String objectType, String objectName) throws SQLException {
		String codeDependencies = QueryList.getQuery("codeDependencies");
		String constraints = QueryList.getQuery("constraints");
		String synonyms = QueryList.getQuery("synonyms");
		String conUrl = (String)dbProperties.get(dbName);
		String password = (String)dbProperties.get(schema);
		PreparedStatement stmt;

		List<ObjectParent> lp = new ArrayList<ObjectParent>();
		ObjectParent op;

		Connection conn = DriverManager.getConnection(conUrl,schema,password);
		
		//System.out.println("objectType is:" + objectType + " Object Name is:" + objectName);
		
	    if (objectType.equalsIgnoreCase("dml")) {
	    	stmt = conn.prepareStatement(constraints);
	    	stmt.setString(1, objectName.toUpperCase());
	    }
	    else if (objectType.equalsIgnoreCase("synonym")) {
	    	stmt = conn.prepareStatement(synonyms);
	    	stmt.setString(1, objectName.toUpperCase());
	    }
	    else {
	    	stmt = conn.prepareStatement(codeDependencies);
	    	stmt.setString(1, objectName.toUpperCase());
	    }
	    ResultSet rs = stmt.executeQuery();
	    while(rs.next()){
	    	op = new ObjectParent();
	    	op.name = rs.getString("NAME");
	    	op.objectType = rs.getString("OBJECT_TYPE");
	    	op.parentDbLink = rs.getString("PARENT_DB_LINK");
	    	op.parentName = rs.getString("PARENT_NAME");
	    	op.parentOwner = rs.getString("PARENT_OWNER");
	    	op.parentType = rs.getString("PARENT_TYPE");
	    	lp.add(op);
	    }
        return lp;
	}
    
	/*
	public long resequenceDMLs(Instruction[] dmls, long startIndex) {
		long i = startIndex;
		for (Instruction dml:dmls) {
			if (dml.dependencies == null || dml.dependencies.length == 0) {
				if (dml.instructionSequence == 0) {
					dml.instructionSequence = i; 
					i++;
				}
			}
			else {
				i = resequenceDMLs(dml.dependencies, i);
				dml.instructionSequence = i;
				i++;
			}
		}
		return i;
	}
	*/
	
	public void getDependencies(List<Instruction> instructions, String objectType) throws SQLException {
		List<Instruction> dmls, codes, synonyms;
		Optional<Instruction> parentInstruction;
		if (objectType.equalsIgnoreCase("dml")) {
			dmls = instructions.stream().filter(item->item.objectType.equalsIgnoreCase("dml")).collect(Collectors.toList());
			for(Instruction dml: dmls) {
				List<ObjectParent> lop = findDependencies(dml.dbName, dml.schemaName, dml.objectType, dml.objectName);
				for (ObjectParent op: lop){
					System.out.println(dml.fileName + ">>" + op.name + ">>" + op.parentName);
					dml.getPredecessors( instructions, dml.dbName, op.parentOwner, dml.objectType, op.parentName);
				}
			}// dml for loop.
		}// if objectType == dml
		else if(objectType.equalsIgnoreCase("code")) {
			codes = instructions.stream().filter(item->item.objectType.equalsIgnoreCase("function") || item.objectType.equalsIgnoreCase("procedure") || item.objectType.equalsIgnoreCase("package") || item.objectType.equalsIgnoreCase("v")).collect(Collectors.toList());
			for(Instruction code: codes) {
				List<ObjectParent> lop = findDependencies(code.dbName, code.schemaName, code.objectType, code.objectName);
				for (ObjectParent op: lop){
					System.out.println(code.fileName + ">>" + op.name + ">>" + op.parentType + ">>" + op.parentName);
					code.getPredecessors( instructions, op.parentDbLink, op.parentOwner, op.parentType, op.parentName);
				}
			}// code for loop.
		}
		else if(objectType.equalsIgnoreCase("synonym")){
			synonyms = instructions.stream().filter(item->item.objectType.equalsIgnoreCase("synonym")).collect(Collectors.toList());
			for(Instruction synonym: synonyms) {
				List<ObjectParent> lop = findDependencies(synonym.dbName, synonym.schemaName, synonym.objectType, synonym.objectName);
				for (ObjectParent op: lop){
					System.out.println(synonym.fileName + ">>" + op.name + ">>" + op.parentType + ">>" + op.parentName);
					synonym.getPredecessors( instructions, op.parentDbLink, op.parentOwner, op.parentType, op.parentName);
					if (op.parentDbLink != null && op.parentDbLink.equalsIgnoreCase(synonym.dbName)) {
						synonym.parent.parent.parent.getPredecessors(databases, op.parentDbLink);
					}
					else if(!op.parentOwner.equalsIgnoreCase(synonym.schemaName)){
						
					}
				}
			}// code for loop.
		}
	}
	
	public void resequenceList() throws SQLException {
		List<Instruction> instructions = databases.stream().
				flatMap(item->item.schemas.stream()).
				flatMap(item->item.objectTypes.stream()).flatMap(item->item.instructions.stream()).
				collect(Collectors.toList());
		
		List<Instruction> dmls = instructions.stream().filter(item->item.objectType.equalsIgnoreCase("dml")).collect(Collectors.toList());
		
		getDependencies(dmls, "dml");
		
		//----------- need to work here --------------
		dmls.forEach(Instruction::resequence);
		dmls.forEach(Instruction::resequence);
		dmls.forEach(Instruction::resequence);
		
		List<Instruction> exceptions = new ArrayList<Instruction>();
		
		List<Instruction> codes = instructions.stream().filter(item->item.objectType.equalsIgnoreCase("function") ||
																	 item.objectType.equalsIgnoreCase("procedure") ||
																	 item.objectType.equalsIgnoreCase("package") ||
																	 item.objectType.equalsIgnoreCase("view") 
				).collect(Collectors.toList());
		
		getDependencies(codes, "code");
		codes.forEach(Instruction::resequence);
		
		List<Instruction> synonyms = instructions.stream().filter(item->item.objectType.equalsIgnoreCase("synonym")).collect(Collectors.toList());
		
		getDependencies(synonyms, "synonym");
		synonyms.forEach(Instruction::resequence);
		
		//resequenceDMLs(dmls.toArray(new Instruction[0]), 1);
		//resequenceDMLs(codes.toArray(new Instruction[0]), 1);
		//instructions.stream().forEach(item->item.recomputeSequence());
	}

}
