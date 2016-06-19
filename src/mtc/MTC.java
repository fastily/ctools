package mtc;

import static mtc.Config.fdump;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import jwiki.core.MQuery;
import jwiki.core.NS;
import jwiki.core.WTask;
import jwiki.core.Wiki;
import jwiki.util.FError;
import jwiki.util.FL;
import jwikix.util.StrTool;
import jwikix.util.TParse;

/**
 * Business Logic for MTC
 * 
 * @author Fastily
 *
 */
public final class MTC
{
	/**
	 * Files with these categories should not be transferred.
	 */
	private final ArrayList<String> blacklist;

	/**
	 * Files must be members of at least one of the following categories to be eligible for transfer.
	 */
	private final ArrayList<String> whitelist = FL.toSAL("Category:All free media", "Category:Self-published work",
			"Category:GFDL files with disclaimers");
	/**
	 * The Wiki objects
	 */
	protected final Wiki enwp, com;

	/**
	 * Creates the regular expression matching Copy to Wikimedia Commons
	 */
	protected final String tRegex;

	/**
	 * Flag indicating whether this is a debug-mode/dry run (do not perform transfers)
	 */
	protected boolean dryRun = false;

	/**
	 * Flag indicating whether the non-free content filter is to be ignored.
	 */
	protected boolean ignoreFilter = false;

	protected DGen dgen;

	/**
	 * Initializes the Wiki objects and download folders for MTC.
	 * 
	 * @param wiki A logged-in Wiki object
	 * 
	 * @throws Throwable On IO error
	 */
	protected MTC(Wiki wiki) throws Throwable
	{
		// Generate download directory
		if (Files.isRegularFile(Config.fdPath))
			FError.errAndExit(Config.fdump + " is file, please remove it so MTC can continue");
		else if (!Files.isDirectory(Config.fdPath))
			Files.createDirectory(Config.fdPath);

		// Initialize Wiki objects
		com = wiki.getWiki("commons.wikimedia.org");
		enwp = wiki.getWiki("en.wikipedia.org");

		dgen = new DGen(enwp, com);

		tRegex = TParse.makeTemplateRegex(enwp, "Template:Copy to Wikimedia Commons");
		blacklist = enwp.getLinksOnPage("Wikipedia:MTC!/Blacklist", NS.CATEGORY);
	}

	/**
	 * Filters (if enabled) and resolves Commons filenames for transfer candinates
	 * 
	 * @param titles The local files to transfer
	 * @return An ArrayList of TransferObject objects.
	 */
	protected ArrayList<TransferObject> filterAndResolve(ArrayList<String> titles)
	{
		return FL.toAL(resolveFileNames(!ignoreFilter ? canTransfer(titles) : titles).entrySet().stream()
				.map(e -> new TransferObject(e.getKey(), e.getValue())));
	}

	/**
	 * Find available file names on Commons for each enwp file. The enwp filename will be returned if it is free on
	 * Commons, otherwise it will be permuted.
	 * 
	 * @param l The list of enwp files to find a Commons filename for
	 * @return The Map such that [ enwp_filename : commons_filename ]
	 */
	private HashMap<String, String> resolveFileNames(ArrayList<String> l)
	{
		HashMap<String, String> m = new HashMap<>();
		for (Map.Entry<String, Boolean> e : MQuery.exists(com, l).entrySet())
		{
			String title = e.getKey();

			if (!e.getValue())
				m.put(title, title);
			else
			{
				String comFN;
				do
				{
					comFN = StrTool.permuteFileName(enwp.nss(title));
				} while (com.exists(comFN)); // loop until available filename is found

				m.put(title, comFN);
			}
		}

		return m;
	}

	/**
	 * Performs checks to determine if a file can be transfered to Commons.
	 * 
	 * @param title The title to check
	 * @return True if the file can <ins>probably</ins> be transfered to Commons.
	 */
	private ArrayList<String> canTransfer(ArrayList<String> titles)
	{
		ArrayList<String> l = FL.toAL(MQuery.getSharedDuplicatesOf(enwp, titles).entrySet().stream()
				.filter(e -> e.getValue().size() == 0).map(Map.Entry::getKey));
		return l.isEmpty() ? l
				: FL.toAL(MQuery.getCategoriesOnPage(enwp, l).entrySet().stream()
						.filter(e -> !StrTool.arraysIntersect(e.getValue(), blacklist) && StrTool.arraysIntersect(e.getValue(), whitelist))
						.map(Map.Entry::getKey));
	}
	
	
	protected  class TransferObject
	{
		/**
		 * The enwp, basefilename (just basename) filenames
		 */
		protected final String wpFN, baseFN;

		/**
		 * The commons and local filenames
		 */
		private final String comFN, localFN;

		/**
		 * Constructor, creates a TransferObject
		 * 
		 * @param wpFN The enwp title to transfer
		 * @param comFN The commons title to transfer to
		 */
		protected TransferObject(String wpFN, String comFN)
		{
			this.comFN = comFN;
			this.wpFN = wpFN;
			baseFN = enwp.nss(wpFN);
			localFN = fdump + baseFN;
		}

		/**
		 * Attempts to transfer an enwp file to Commons
		 * 
		 * @return True on success.
		 */
		protected boolean doTransfer()
		{
			String t = dgen.generate(wpFN);

			if (dryRun)
			{
				System.out.println(t);
				return true;
			}
			else if (t != null && WTask.downloadFile(wpFN, localFN, enwp)
					&& com.upload(Paths.get(localFN), comFN, t, String.format(Config.tFrom, wpFN)))
				return enwp.edit(wpFN, String.format("{{subst:ncd|%s}}%n", comFN) + enwp.getPageText(wpFN).replaceAll(tRegex, ""),
						Config.tTo);

			return false;
		}

	}
}