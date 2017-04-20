package mtc;

import java.nio.file.Path;
import java.nio.file.Paths;

import fastily.jwiki.util.FSystem;

/**
 * Configurable, frequently used constant Strings for MTC.
 * 
 * @author Fastily
 *
 */
public class MStrings
{
	/**
	 * Short name for MTC!
	 */
	protected static final String name = "MTC!"; 
	
	/**
	 * The full name of MTC!, with the namespace prefix.
	 */
	protected static final String fullname = "Wikipedia:" + name;
	
	/**
	 * The directory pointing to the location for file downloads
	 */
	protected static final String fdump = "mtcfiles" + FSystem.psep;

	/**
	 * The Path object pointing to {@code fdump}
	 */
	protected static final Path fdPath = Paths.get(fdump);

	/**
	 * Basic wikitext link to MTC!. For use in edit summaries.
	 */
	protected static final String mtcLink = String.format("[[%s|%s]]", fullname, name);

	/**
	 * Interwiki link from Commons to the enwp project page for MTC!.
	 */
	protected static final String mtcComLink = String.format("[[w:%s|%s]]", fullname, name);
	
	/**
	 * Format String edit summary for files uploaded to Commons for Commons
	 */
	protected static final String tFrom = String.format("Transferred from [[w:%%s|en.wikipedia]] (%s)", mtcComLink);

	/**
	 * Edit summary for files transferred to Commons on enwp
	 */
	protected static final String tTo = String.format("Transferred to Commons (%s)", mtcLink);
	
	/**
	 * The URL base for the English Wikipedia.
	 */
	protected static final String enwpURLBase = "https://en.wikipedia.org/wiki/";
	
	/**
	 * Speedy deletion F8 reason format string.
	 */
	protected static final String f8Fmt = "[[WP:CSD#F8|F8]]: Media file available on Commons: [[:%s]]";
	
	/**
	 * Constructors disallowed.
	 */
	private MStrings()
	{

	}
}