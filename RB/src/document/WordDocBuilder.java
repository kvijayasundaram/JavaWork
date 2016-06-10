package document;
import helper.Database;
import helper.DbSchema;
import helper.Instruction;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.poi.hwpf.usermodel.Paragraph;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.hwpf.usermodel.Section;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHyperlink;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

public class WordDocBuilder {
	
	private XWPFDocument document; 
	private File file;
	private String rbRoot = "C:\\ReleaseNotesRoot";
    private String templatePath = rbRoot;
    private String buildPropFile;
    private String projectPropFile;
    private String svnTag = "svn://svnserver.cscdev.com/oracle/database/tags/qa";
    private int buildNo;
	private Map<String, String> settings;
	public String tarFileName;

	public WordDocBuilder(String filePath) throws IOException 
	{
        this.projectPropFile = filePath + "\\build\\project.properties";

		Properties properties2  = new Properties();
		properties2.load(new FileInputStream(new File(projectPropFile)));
		
		String buildNumber = formatBuild(properties2.getProperty("buildNumber"));

		this.buildPropFile = this.rbRoot + "\\" + properties2.getProperty("buildType") + ".properties";
		LocalDate ld = LocalDate.now();
		String label = "DEVELOPMENT_" + ld.format(DateTimeFormatter.ofPattern("yyyyMMdd")).toString() + "_V" + 
		properties2.getProperty("versionNumber")+ ".b" + buildNumber;
		
		Properties properties = new Properties();
		properties.load(new FileInputStream(new File(buildPropFile)));
		
		svnTag = svnTag + "/" + properties.getProperty("dbName") + "/" + properties2.getProperty("projectName") + "-" + properties2.getProperty("buildType")  + "-" + label;
		
		templatePath = templatePath + "\\" + properties2.getProperty("buildType")  + "\\Release_Notes_template.docx";
		
		properties.putAll(properties2);
		properties.put("label", label);
		properties.put("pvcsLabel", label);
		properties.put("svnPath", svnTag);
		properties.put("date", ld.format(DateTimeFormatter.ofPattern("MM/dd/yyyy")));
		properties.put("documentHeading", properties.getProperty("projectCode")+ " " + properties.getProperty("applicationName"));
		properties.put("buildDescription", "Build " + properties.getProperty("buildNumber"));
		
		tarFileName = properties.getProperty("tarFilePrefix") + 
				"_v" + properties2.getProperty("versionNumber").replaceAll("\\.", "_") +
				"_build" + String.format("%02d", Integer.parseInt(buildNumber)) +".tar";
		
		properties.put("tarFileName", tarFileName);
		properties.put("addedFileName", properties.getProperty("addedFilePrefix")+ "_v"  
		        + properties.getProperty("versionNumber").replace('.', '_')
				+ "_build" + buildNumber + ".tar");
		
		properties.put("addedFileLabel", label);
		
		settings = new HashMap<String, String>((Map) properties);
		buildNo = Integer.parseInt(properties.getProperty("buildNumber"));
		
		
	    String relNotesPath = filePath + "\\build\\" + settings.get("releaseNotesName");
		Files.copy(Paths.get(templatePath), Paths.get(relNotesPath), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
		file =  new File(relNotesPath);
        System.out.println("relNotesPath:" + relNotesPath);
		if (Files.exists(Paths.get(filePath))) {
			document= new XWPFDocument(new FileInputStream(file));
			replaceTemplateInfo(document);
		}

	}
	
	private void writeInstructions(List<String> instructions, XWPFParagraph paragraph, 
			String color, int fontsize, String fontFamily){
		XWPFRun run=paragraph.createRun();
		run.setFontFamily(fontFamily);
		run.setFontSize(fontsize);
		run.setColor(color); 
		for(String instruction : instructions)
		{
			if (instruction != null && instruction.trim() != "") {
				run.setText(instruction);
				//run.addCarriageReturn();
				run.addBreak();
			}
		}
	}
	
	public void writeSchema(XWPFTableCell cell, DbSchema dbSchema, Instruction.PrintType pt){

		XWPFParagraph paragraph = cell.addParagraph();
		XWPFRun run = paragraph.createRun();
		run.setFontSize(15);
		run.setFontFamily("Arial");
		run.setColor("000000"); // black
		run.setUnderline(UnderlinePatterns.SINGLE);
		run.setTextPosition(0);
		run.setText("connect " + dbSchema.schemaName + "@" + dbSchema.dbName);
		//--------------
		XWPFParagraph paragraph2 = cell.addParagraph();
		if(pt == Instruction.PrintType.DEPLOYMENT) {
			writeInstructions(dbSchema.preRequisiteMessages.stream().collect(Collectors.toList()), paragraph2, "FF0000", 9, "Arial");
		}
		//--------------
		writeInstructions( dbSchema.getInstructions(pt), paragraph2, "000000", 8, "Arial");
		//--------------
		writeInstructions(dbSchema.postDeploymentMessages.stream().collect(Collectors.toList()), paragraph2, "FF0000", 9, "Arial");
		
	}
	
	public void write(List<Database> databases){
		
		XWPFTableCell prodCell = findInstructionsCell(document, "Production Instructions");
		
		for(Database localDatabase:databases.stream().sorted(Comparator.comparing(item->item.dbSequence)).collect(Collectors.toList())){
			for(DbSchema dbSchema : localDatabase.schemas.stream().sorted(Comparator.comparing(item->item.schemaSequence)).collect(Collectors.toList())){
				writeSchema(prodCell, dbSchema, Instruction.PrintType.DEPLOYMENT);
			}
		}
		
		XWPFTableCell rollBackCell = findInstructionsCell(document, "Rollback Instructions");
		
		for(Database localDatabase:databases.stream().sorted(Comparator.comparing(item->-(item.dbSequence))).collect(Collectors.toList())){
			for(DbSchema dbSchema : localDatabase.schemas.stream().sorted(Comparator.comparing(item-> -(item.schemaSequence))).collect(Collectors.toList())){
				writeSchema(rollBackCell, dbSchema, Instruction.PrintType.BACKOUT);
			}
		}
		
		XWPFTableCell qaCell = findInstructionsCell(document, "QA Instructions");
		if (buildNo == 1) {
			
		}
		
	}
	
	private void resetParagraph(XWPFParagraph para, String text){
		for (int i=0; i <= para.getRuns().size(); i++) { 
			System.out.println("removing text:" + para.getRuns().get(i).text());
		    para.removeRun(i);
		}
		para.createRun().setText(text);
	}
	
	private void resetCell(XWPFTableCell cell, String text){
		XWPFParagraph para = cell.getParagraphs().get(0);
        XWPFRun run = para.getRuns().get(0);
        String style = para.getStyle();
        String  color = run.getColor();
        String  ff = run.getFontFamily();
        int     fs = run.getFontSize();
		for (int i=0; i < cell.getParagraphs().size(); i++) { 
			
		    cell.removeParagraph(i);
		}
		para = cell.addParagraph();
		para.setStyle(style);
		run = para.createRun();
		run.setFontSize(fs);
		run.setFontFamily(ff);
		run.setColor(color);
		run.setText(text);
	}
	
	private void replaceTemplateInfo(XWPFDocument doc){
		Set<String> keys = settings.keySet();
		List<XWPFTableRow> rows = doc.getTables().stream().flatMap(item->item.getRows().stream()).collect(Collectors.toList());
		List<XWPFTableCell> cells = rows.stream().flatMap(item->item.getTableCells().stream()).collect(Collectors.toList());
		
		int counter = 1;
		int runCount = 1;
		XWPFRun run;
		for(String key:keys) {
			for (XWPFParagraph para:doc.getParagraphs())  {	
				counter = 1;
				if (para.getText().contains("<" + key + ">")) {
					System.out.println("@@@@ para found " + key);
					 resetParagraph( para, settings.get(key));
				}
			}
			//--
			for (XWPFTableCell cell:cells)  {	
				if (cell.getText().contains("<" + key + ">")) {
					System.out.println("@@@@ cell found " + key);
					resetCell(cell, settings.get(key));
				}
			}
			//--
		}
	}

	public void writeToFile() throws IOException {
		FileOutputStream out = new FileOutputStream(file);
		document.write(out);
		document.close();
		out.close();
		System.out.println("word document written successully");
	}
	
	public String formatBuild(String buildNumber) {
		try {
			return String.format("%02d", Integer.parseInt(buildNumber));
		}
		catch(Exception ex) {
		    return buildNumber;	
		}
	}
	
	public XWPFTableCell findInstructionsCell(XWPFDocument doc, String search){
		XWPFTableCell cell = null;
		XWPFTable table = null;
		XWPFTableRow row = null;
		List<XWPFTableRow> rows = doc.getTables().stream().flatMap(item->item.getRows().stream()).collect(Collectors.toList());
		List<XWPFTableCell> cells = rows.stream().flatMap(item->item.getTableCells().stream()).filter(item->item.getText().contains(search)).collect(Collectors.toList());
		if (cells != null && cells.size() > 0) {
			cell= cells.get(0);
			table = cells.get(0).getTableRow().getTable();
			row = table.getRows().get(1);
			cell = row.getCell(0);
			// for QA instructions always create a new row.
			if (search.equalsIgnoreCase("QA Instructions") ) {
					row = table.insertNewTableRow(1);
					cell = row.createCell();
			}
		}
		return cell;
	}
}
