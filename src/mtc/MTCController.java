package mtc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import fastily.jwiki.core.NS;
import fastily.jwiki.core.Wiki;
import fastily.jwiki.util.FError;
import fastily.jwiki.util.FL;
import jwikix.ui.FXTool;
import mtc.MTC.TransferObject;
import util.Toolbox;

/**
 * An MTC UI window
 * 
 * @author Fastily
 *
 */

public class MTCController
{
	/**
	 * The ProgressBar for the UI
	 */
	@FXML
	protected ProgressBar pb;

	/**
	 * The ComboBox for mode selection
	 */
	@FXML
	protected ComboBox<String> cb;

	/**
	 * The TextField for user input
	 */
	@FXML
	protected TextField tf;

	/**
	 * The transfer Button
	 */
	@FXML
	protected Button transferButton;

	/**
	 * The paste Button
	 */
	@FXML
	protected Button pasteButton;

	/**
	 * The CheckBox to disable the NFC filter.
	 */
	@FXML
	protected CheckBox disableFilterBox;

	/**
	 * The user Label and ProgressBar
	 */
	@FXML
	protected Label userLabel, cntLabel, pbLabel;

	/**
	 * The root Node object for the UI
	 */
	private Parent root;

	/**
	 * The Wiki objects to use with MTC.
	 */
	private Wiki wiki;

	/**
	 * The master HashSet of successfully transferred items
	 */
	private HashSet<String> tfl = new HashSet<>();
	
	/**
	 * The MTC instance for this Controller.
	 */
	private static MTC mtc;

	/**
	 * The OS clipboard
	 */
	private static final Clipboard clipboard = Clipboard.getSystemClipboard();

	/**
	 * The MTCController
	 * 
	 * @param wiki The Wiki object to use
	 * @return The MTC controller
	 */
	public static MTCController load(Wiki wiki)
	{
		FXMLLoader fl = new FXMLLoader(MTCController.class.getResource("MTC.fxml"));

		Parent root = null;
		try
		{
			root = fl.load();
		}
		catch (Throwable e)
		{
			FXTool.warnUser("Should never reach this point; MTC.fxml is missing?");
			FError.errAndExit(e, null);
		}

		MTCController mc = fl.getController();

		mc.root = root;
		mc.wiki = wiki.getWiki("en.wikipedia.org");

		// Initialize dynamic data for Nodes
		mc.userLabel.setText(mc.wiki.whoami() + " ->");
		mc.cb.getItems().addAll("File", "User", "Category", "Template");

		// Initalize MTC base
		try
		{
			mtc = new MTC(mc.wiki);
		}
		catch (Throwable e)
		{
			FXTool.warnUser("Are you missing filesystem Read/Write Permissions?");
			FError.errAndExit(e, null);
		}

		return mc;
	}

	/**
	 * Transfers files to Commons as per user input.
	 */
	@FXML
	protected void startTransfer()
	{
		String text = tf.getText().trim(), mode = cb.getSelectionModel().getSelectedItem();
		if (text.isEmpty() || mode == null)
		{
			FXTool.warnUser("Please select a transfer mode and specify a File, Category, Username, or Template to continue.");
			return;
		}

		// Initialize and grab values from Nodes
		mtc.ignoreFilter = disableFilterBox.isSelected();
		updatePB(0, "Hold tight, querying server...");
		transferButton.setDisable(true);

		FXTool.runAsyncTask(() -> runTransfer(mode, text));
	}

	/**
	 * Pastes a String (if possible) from the OS clipboard into <code>tf</code>.
	 */
	@FXML
	protected void doPaste()
	{
		String text = clipboard.getString();
		if (text != null)
			tf.setText(text);
	}

	/**
	 * Performs a transfer with the given transfer mode and text.
	 * 
	 * @param mode The mode of transfer
	 * @param text The input text by the user.
	 */
	private void runTransfer(String mode, String text)
	{
		ArrayList<String> fl;
		switch (mode)
		{
			case "File":
				fl = FL.toSAL(wiki.convertIfNotInNS(text, NS.FILE));
				break;
			case "Category":
				fl = wiki.getCategoryMembers(wiki.convertIfNotInNS(text, NS.CATEGORY), NS.FILE);
				break;
			case "User":
				fl = wiki.getUserUploads(wiki.nss(text));
				break;
			case "Template":
				fl = wiki.whatTranscludesHere(wiki.convertIfNotInNS(text, NS.TEMPLATE), NS.FILE);
				break;
			default:
				fl = new ArrayList<>();
				break;
		}

		Set<String> fails = Collections.synchronizedSet(new HashSet<>()), success = Collections.synchronizedSet(new HashSet<>());

		ArrayList<TransferObject> tol = mtc.filterAndResolve(fl);
		if (tol.isEmpty())
		{
			Platform.runLater(() -> {
				updatePB(0, "No matching files found! :(");
				FXTool.warnUser("I couldn't find any file(s) matching your query; please verify that your input is correct");
				transferButton.setDisable(false);
			});

			return;
		}

		int total = tol.size();
		double denom = (double) total;
		AtomicInteger i = new AtomicInteger();

		tol.stream().forEach(to -> {
			Platform.runLater(() -> updatePB(((double) i.getAndIncrement()) / denom,
					String.format("Transferring (%d/%d): %s", i.get(), total, to.wpFN)));
			if (to.doTransfer())
				success.add(to.wpFN);
			else
				fails.add(to.wpFN);
		});

		if (!fails.isEmpty())
			Platform.runLater(() -> FXTool
					.warnUser(String.format("Task complete, with %d failures:%n%n%s", fails.stream().collect(Collectors.joining("\n")))));

		tfl.addAll(success);
		Platform.runLater(() -> {
			updatePB(1, String.format("OK: Completed %d transfer(s)", success.size()));
			cntLabel.setText("" + tfl.size());
			transferButton.setDisable(false);
		});
	}

	/**
	 * Updates the ProgressBar and its Label.
	 * 
	 * @param pos The percentage to set the ProgressBar to
	 * @param msg The message to display above the ProgressBar
	 */
	private void updatePB(double pos, String msg)
	{
		pb.setProgress(pos);
		pbLabel.setText(msg);
	}

	/**
	 * Gets this controller's root Node
	 * 
	 * @return The root Node.
	 */
	public Parent getRoot()
	{
		return root;
	}

	/**
	 * Posts a list of files transferred in the previous session to the user's transfer log.
	 */
	protected void dumpLog()
	{
		if (!tfl.isEmpty())
			wiki.edit(String.format("User:%s/MTC! Transfer Log", wiki.whoami()),
					Toolbox.listify(String.format("== ~~~~~ - v%s ==%n", MTCui.version), tfl, true), String.format("Update Transfer log (%s)", Config.mtcLink));
	}
}