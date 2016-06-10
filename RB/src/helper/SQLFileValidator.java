package helper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SQLFileValidator {

	private String codeText;
	private String RollbackText;
	boolean isLineComment = false;
	private boolean isBlockComment = false;
	public List<String> contents = new ArrayList<String>();
	public List<String> comments = new ArrayList<String>();
	private final String commentStartPattern = "/\\*";
	private final String commentEndPattern = "\\*\\/";
	
	private final Pattern currentBuildComment = Pattern.compile( "\\s*--\\s*CURRENT", Pattern.CASE_INSENSITIVE);
	
	public String getContentText () {
		StringBuilder sb = new StringBuilder();
		for(String s:contents)
			sb.append(s+"\n");
		return sb.toString();
	}
	
	public String getCommentText () {
		StringBuilder sb = new StringBuilder();
		for(String s:comments)
			sb.append(s+"\n");
		return sb.toString();
	}
	
	public void parse(File file, String objectType)throws IOException{
		List<String> lines = Files.readAllLines(file.toPath());
		List<String> incrementalLines = new ArrayList<String>();
		Consumer<String> checker = (text) -> {
			//System.out.println(text);	
			String[] lineParts;

			if (!isBlockComment)	{
				if (text.contains("/*")) {
					if (text.indexOf("--") < 0 || text.indexOf("/*") < text.indexOf("--")) {
						lineParts = text.split(commentStartPattern, 2);
						isBlockComment = true;
						if (lineParts.length > 0 && lineParts[0] != null && !lineParts[0].isEmpty() )
							contents.add(lineParts[0]);
					}
					else
						isLineComment = true;
				}
				else if(text.contains("--")) {
					if (text.indexOf("/*") < 0 || text.indexOf("--") < text.indexOf("/*")) {
					    isLineComment = true;
					}
				}
				else 
					contents.add(text);
			}
			else if (text.contains("*/")) {
				lineParts = text.split(commentEndPattern, 2);
				if (lineParts.length > 1 && lineParts[1] != null && !lineParts[1].isEmpty() )
					comments.add(lineParts[0]);
				    if(lineParts[1].indexOf("--")>0)
				    	contents.add(lineParts[1].split("--")[0]);
				    else
				        contents.add(lineParts[1]);
				isBlockComment = false;
			}
			
			if (isLineComment){
				lineParts = text.split("--", 2);
				if (lineParts.length > 0 && lineParts[0] != null && !lineParts[0].isEmpty() )
				    contents.add(lineParts[0]);	
				if(lineParts.length > 1 && lineParts[1] != null && !lineParts[1].isEmpty() )
				    comments.add(lineParts[1]);
			}
		};
		
		if(objectType.equalsIgnoreCase("dml")) {
			for(String line:lines){
				if(currentBuildComment.matcher(line).find())
					incrementalLines.clear();
				else
					incrementalLines.add(line);
			}
			lines = incrementalLines;
		}
		lines.stream().map(text -> text.trim()).forEach(checker);
	}
}
