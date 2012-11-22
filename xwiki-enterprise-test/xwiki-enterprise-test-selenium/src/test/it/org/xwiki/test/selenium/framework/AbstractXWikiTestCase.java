/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.test.selenium.framework;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Map.Entry;
import java.util.Properties;

import junit.framework.TestCase;

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.internal.WrapsDriver;

import com.thoughtworks.selenium.Selenium;
import com.thoughtworks.selenium.Wait;

/**
 * All XWiki Selenium tests must extend this class.
 * 
 * @version $Id$
 */
public abstract class AbstractXWikiTestCase extends TestCase implements SkinExecutor
{
    public static final String BASEDIR = System.getProperty("basedir");

    public static final String DOC = "selenium.browserbot.getCurrentWindow().document.";

    private static final int WAIT_TIME = 30000;

    private SkinExecutor skinExecutor;

    private Selenium selenium;

    /** Cached secret token. TODO cache for each user. */
    private static String secretToken = null;

    /**
     * @return the {@link WebDriver} instance
     */
    protected WebDriver getDriver()
    {
        return ((WrapsDriver) getSelenium()).getWrappedDriver();
    }

    public void setSkinExecutor(SkinExecutor skinExecutor)
    {
        this.skinExecutor = skinExecutor;
    }

    public SkinExecutor getSkinExecutor()
    {
        if (this.skinExecutor == null) {
            throw new RuntimeException("Skin executor hasn't been initialized. Make sure to wrap " + "your test in a "
                + XWikiTestSuite.class.getName() + " class and call "
                + " addTestSuite(Class testClass, SkinExecutor skinExecutor).");
        }
        return this.skinExecutor;
    }

    public void setSelenium(Selenium selenium)
    {
        this.selenium = selenium;
    }

    public Selenium getSelenium()
    {
        return this.selenium;
    }

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();

        // Print test name for easier parsing of Selenium logs
        System.out.println("Test: " + getName());

        if (AbstractXWikiTestCase.secretToken == null) {
            recacheSecretToken();
        }
    }

    /**
     * Capture test failures in order to output the HTML for easier debugging + take screenshot.
     */
    @Override
    public void runBare() throws Throwable
    {
        Throwable exception = null;
        setUp();
        try {
            runTest();
        } catch (Throwable running) {
            exception = running;
            // Take screenshot before the tear down to ensure we take a picture of the real problem.
            takeScreenShot();
        } finally {
            try {
                tearDown();
            } catch (Throwable tearingDown) {
                if (exception == null)
                    exception = tearingDown;
            }
        }
        if (exception != null)
            throw exception;
    }

    private void takeScreenShot() throws Throwable
    {
        try {
            // Selenium method execution results are logged automatically by Selenium so just calling getHtmlSource
            // is enough to have it in the logs.
            getSelenium().getHtmlSource();

            // Create directory where to store screenshots
            String screenshotDir = BASEDIR;
            if (!screenshotDir.endsWith(System.getProperty("file.separator"))) {
                screenshotDir = screenshotDir + System.getProperty("file.separator");
            }
            screenshotDir =
                screenshotDir + "target" + System.getProperty("file.separator") + "selenium-screenshots"
                    + System.getProperty("file.separator");
            new File(screenshotDir).mkdirs();

            // Capture screenshot
            String testName = this.getClass().getName() + "-" + getName();
            File screenshotFile = new File(screenshotDir, testName + ".png");
            FileUtils.copyFile(((TakesScreenshot) getDriver()).getScreenshotAs(OutputType.FILE), screenshotFile);
            throw new Exception(String.format("Screenshot for failing test [%s] saved at [%s]", testName,
                screenshotFile.getAbsolutePath()));
        } catch (Throwable t) {
            // Don't throw any exception generated by the debugging steps
            t.printStackTrace();
        }
    }

    // Convenience methods wrapping Selenium

    public void open(String url)
    {
        getSelenium().open(url);
    }

    public void open(String space, String page)
    {
        open(getUrl(space, page));
    }

    public void open(String space, String page, String action)
    {
        open(getUrl(space, page, action));
    }

    public void open(String space, String page, String action, String queryString)
    {
        open(getUrl(space, page, action, queryString));
    }

    public String getTitle()
    {
        return getSelenium().getTitle();
    }

    public void assertPage(String space, String page)
    {
        assertTrue(getTitle().matches(".*\\(" + space + "." + page + "\\) - XWiki"));
    }

    /**
     * Visits the specified page and checks if it exists, coming back to the current page.
     * 
     * @param space the space name
     * @param page the page name
     * @return {@code true} if the specified page exists
     */
    public boolean isExistingPage(String space, String page)
    {
        String saveUrl = getSelenium().getLocation();

        open(getUrl(space, page));
        boolean exists = isExistingPage();

        // Restore original URL
        open(saveUrl);

        return exists;
    }

    /**
     * @return {@code true} if we are on an existing page, {@code false} otherwise
     */
    public boolean isExistingPage()
    {
        return !getSelenium().isTextPresent("The requested document could not be found.");
    }

    public void assertTitle(String title)
    {
        assertEquals(title, getTitle());
    }

    public boolean isElementPresent(String locator)
    {
        return getSelenium().isElementPresent(locator);
    }

    public boolean isLinkPresent(String text)
    {
        return isElementPresent("link=" + text);
    }

    public void clickLinkWithText(String text)
    {
        clickLinkWithText(text, true);
    }

    public void assertTextPresent(String text)
    {
        assertTrue("[" + text + "] isn't present.", getSelenium().isTextPresent(text));
    }

    public void assertTextNotPresent(String text)
    {
        assertFalse("[" + text + "] is present.", getSelenium().isTextPresent(text));
    }

    public void assertElementPresent(String elementLocator)
    {
        assertTrue("[" + elementLocator + "] isn't present.", isElementPresent(elementLocator));
    }

    public void assertElementNotPresent(String elementLocator)
    {
        assertFalse("[" + elementLocator + "] is present.", isElementPresent(elementLocator));
    }

    public void waitPage()
    {
        waitPage(WAIT_TIME);
    }

    /**
     * @deprecated use {@link #waitPage()} instead
     */
    @Deprecated
    public void waitPage(int nbMillisecond)
    {
        getSelenium().waitForPageToLoad(String.valueOf(nbMillisecond));
    }

    public void createPage(String space, String page, String content)
    {
        createPage(space, page, content, null);
    }

    public void createPage(String space, String page, String content, String syntax)
    {
        // If the page already exists, delete it first
        deletePage(space, page);
        if (syntax == null) {
            editInWikiEditor(space, page);
        } else {
            editInWikiEditor(space, page, syntax);
        }
        setFieldValue("content", content);
        clickEditSaveAndView();
    }

    public void deletePage(String space, String page)
    {
        open(space, page, "delete", "confirm=1");
    }

    public void restorePage(String space, String page)
    {
        open(space, page, "view");
        if (getSelenium().isTextPresent("Restore")) {
            clickLinkWithText("Restore", true);
        }
    }

    public void clickLinkWithLocator(String locator)
    {
        clickLinkWithLocator(locator, true);
    }

    public void clickLinkWithLocator(String locator, boolean wait)
    {
        assertElementPresent(locator);
        getSelenium().click(locator);
        if (wait) {
            waitPage();
        }
    }

    public void clickLinkWithText(String text, boolean wait)
    {
        clickLinkWithLocator("link=" + text, wait);
    }

    public boolean isChecked(String locator)
    {
        return getSelenium().isChecked(locator);
    }

    public String getFieldValue(String fieldName)
    {
        // Note: We could use getSelenium().getvalue() here. However getValue() is stripping spaces
        // and some of our tests verify that there are leading spaces/empty lines.
        return getSelenium().getEval(
            "selenium.browserbot.getCurrentWindow().document.getElementById(\"" + fieldName + "\").value");
    }

    public void setFieldValue(String fieldName, String value)
    {
        getSelenium().type(fieldName, value);
    }

    public void checkField(String locator)
    {
        getSelenium().check(locator);
    }

    public void submit()
    {
        clickLinkWithXPath("//input[@type='submit']");
    }

    public void submit(String locator)
    {
        clickLinkWithLocator(locator);
    }

    public void submit(String locator, boolean wait)
    {
        clickLinkWithLocator(locator, wait);
    }

    public void clickLinkWithXPath(String xpath)
    {
        clickLinkWithXPath(xpath, true);
    }

    public void clickLinkWithXPath(String xpath, boolean wait)
    {
        clickLinkWithLocator("xpath=" + xpath, wait);
    }

    public void waitForCondition(String condition)
    {
        getSelenium().waitForCondition(condition, "" + WAIT_TIME);
    }

    public void waitForTextPresent(final String elementLocator, final String expectedValue)
    {
        new Wait()
        {
            public boolean until()
            {
                return getSelenium().getText(elementLocator).equals(expectedValue);
            }
        }.wait(getSelenium().isElementPresent(elementLocator) ? "Element [" + elementLocator + "] not found"
            : "Element [" + elementLocator + "] found but it doesn't have the expected value [" + expectedValue + "]");
    }

    public void waitForTextContains(final String elementLocator, final String containsValue)
    {
        new Wait()
        {
            public boolean until()
            {
                return getSelenium().getText(elementLocator).indexOf(containsValue) > -1;
            }
        }.wait(getSelenium().isElementPresent(elementLocator) ? "Element [" + elementLocator + "] not found"
            : "Element [" + elementLocator + "] found but it doesn't contain the expected value [" + containsValue
                + "]");
    }

    public void waitForBodyContains(final String containsValue)
    {
        new Wait()
        {
            public boolean until()
            {
                return getSelenium().getBodyText().indexOf(containsValue) > -1;
            }
        }.wait("Body text doesn't contain the value [" + containsValue + "]");
    }

    public void waitForElement(final String elementLocator)
    {
        new Wait()
        {
            public boolean until()
            {
                return getSelenium().isElementPresent(elementLocator);
            }
        }.wait("element [" + elementLocator + "] not found");
    }

    /**
     * Waits until an alert message appears or the timeout expires. You can use {@link Selenium#getAlert()} to assert
     * the alert message afterwards.
     */
    public void waitForAlert()
    {
        new Wait()
        {
            public boolean until()
            {
                return getSelenium().isAlertPresent();
            }
        }.wait("The alert didn't appear.");
    }

    /**
     * Waits until a confirmation message appears or the timeout expires. You can use {@link Selenium#getConfirmation()}
     * to assert the confirmation message afterwards.
     */
    public void waitForConfirmation()
    {
        new Wait()
        {
            public boolean until()
            {
                return getSelenium().isConfirmationPresent();
            }
        }.wait("The confirmation didn't appear.");
    }

    public void clickButtonAndContinue(String locator)
    {
        waitForCondition("window.document.getElementsByClassName('xnotification-done')[0] == null");
        submit(locator, false);
        waitForCondition("window.document.getElementsByClassName('xnotification-done')[0] != null");
    }

    @Override
    public void clickEditPage()
    {
        getSkinExecutor().clickEditPage();
    }

    @Override
    public void clickEditPageInWikiSyntaxEditor()
    {
        getSkinExecutor().clickEditPageInWikiSyntaxEditor();
    }

    @Override
    public void clickEditPageInWysiwyg()
    {
        getSkinExecutor().clickEditPageInWysiwyg();
    }

    @Override
    public void clickEditPageAccessRights()
    {
        getSkinExecutor().clickEditPageAccessRights();
    }

    @Override
    public void clickEditPageInlineForm()
    {
        getSkinExecutor().clickEditPageInlineForm();
    }

    @Override
    public void clickDeletePage()
    {
        getSkinExecutor().clickDeletePage();
    }

    @Override
    public void clickCopyPage()
    {
        getSkinExecutor().clickCopyPage();
    }

    @Override
    public void clickShowComments()
    {
        getSkinExecutor().clickShowComments();
    }

    @Override
    public void clickShowAttachments()
    {
        getSkinExecutor().clickShowAttachments();
    }

    @Override
    public void clickShowHistory()
    {
        getSkinExecutor().clickShowHistory();
    }

    @Override
    public void clickShowInformation()
    {
        getSkinExecutor().clickShowInformation();
    }

    @Override
    public void clickEditPreview()
    {
        getSkinExecutor().clickEditPreview();
    }

    @Override
    public void clickEditSaveAndContinue()
    {
        getSkinExecutor().clickEditSaveAndContinue();
    }

    @Override
    public void clickEditCancelEdition()
    {
        getSkinExecutor().clickEditCancelEdition();
    }

    @Override
    public void clickEditSaveAndView()
    {
        getSkinExecutor().clickEditSaveAndView();
    }

    /**
     * Clicks on the add property button in the class editor. As a result the specified property is added to the edited
     * class and the class is saved. This method waits for the class to be saved.
     */
    @Override
    public void clickEditAddProperty()
    {
        getSkinExecutor().clickEditAddProperty();
    }

    /**
     * Clicks on the add object button in the object editor. As a result an object of the specified class is added to
     * the edited document and the document is saved. This method waits for the document to be saved.
     */
    @Override
    public void clickEditAddObject()
    {
        getSkinExecutor().clickEditAddObject();
    }

    @Override
    public boolean isAuthenticated()
    {
        return getSkinExecutor().isAuthenticated();
    }

    @Override
    public boolean isAuthenticated(String username)
    {
        return getSkinExecutor().isAuthenticated(username);
    }

    @Override
    public boolean isAuthenticationMenuPresent()
    {
        return getSkinExecutor().isAuthenticationMenuPresent();
    }

    @Override
    public void logout()
    {
        getSkinExecutor().logout();
        recacheSecretToken();
    }

    @Override
    public void login(String username, String password, boolean rememberme)
    {
        getSkinExecutor().login(username, password, rememberme);
        recacheSecretToken();
    }

    @Override
    public void loginAsAdmin()
    {
        getSkinExecutor().loginAsAdmin();
        recacheSecretToken();
    }

    /**
     * If the user is not logged in already and if the specified user page exists, it is logged in. Otherwise the user
     * is registered first and then the login is executed.
     * 
     * @param username the user name to login as. If the user is to be created, this will also be used as the user first
     *            name while the user last name will be left blank
     * @param password the password of the user
     * @param rememberMe whether the login should be remembered or not
     */
    public void loginAndRegisterUser(String username, String password, boolean rememberMe)
    {
        if (!isAuthenticationMenuPresent()) {
            // navigate to the main page
            open("Main", "WebHome");
        }

        // if user is already authenticated, don't login
        if (isAuthenticated(username)) {
            return;
        }

        // try to go to the user page
        open("XWiki", username);
        // if user page doesn't exist, register the user first
        boolean exists = !getSelenium().isTextPresent("The requested document could not be found.");
        if (!exists) {
            if (isAuthenticated()) {
                logout();
            }
            clickRegister();
            fillRegisterForm(username, "", username, password, "");
            submit();
            // assume registration was done successfully, otherwise the register test should fail too
        }

        login(username, password, rememberMe);
    }

    public void fillRegisterForm(String firstName, String lastName, String username, String password, String email)
    {
        setFieldValue("register_first_name", firstName);
        setFieldValue("register_last_name", lastName);
        setFieldValue("xwikiname", username);
        setFieldValue("register_password", password);
        setFieldValue("register2_password", password);
        setFieldValue("register_email", email);
    }

    @Override
    public void clickLogin()
    {
        getSkinExecutor().clickLogin();
    }

    @Override
    public void clickRegister()
    {
        getSkinExecutor().clickRegister();
    }

    public String getEditorSyntax()
    {
        return getSkinExecutor().getEditorSyntax();
    }

    public void setEditorSyntax(String syntax)
    {
        getSkinExecutor().setEditorSyntax(syntax);
    }

    public void editInWikiEditor(String space, String page)
    {
        getSkinExecutor().editInWikiEditor(space, page);
    }

    public void editInWikiEditor(String space, String page, String syntax)
    {
        getSkinExecutor().editInWikiEditor(space, page, syntax);
    }

    public void editInWysiwyg(String space, String page)
    {
        getSkinExecutor().editInWysiwyg(space, page);
    }

    public void editInWysiwyg(String space, String page, String syntax)
    {
        getSkinExecutor().editInWysiwyg(space, page, syntax);
    }

    public void clearWysiwygContent()
    {
        getSkinExecutor().clearWysiwygContent();
    }

    public void keyPressAndWait(String element, String keycode) throws InterruptedException
    {
        getSelenium().keyPress(element, keycode);
        waitPage();
    }

    public void typeInWysiwyg(String text)
    {
        getSkinExecutor().typeInWysiwyg(text);
    }

    public void typeInWiki(String text)
    {
        getSkinExecutor().typeInWiki(text);
    }

    public void typeEnterInWysiwyg()
    {
        getSkinExecutor().typeEnterInWysiwyg();
    }

    public void typeShiftEnterInWysiwyg()
    {
        getSkinExecutor().typeShiftEnterInWysiwyg();
    }

    public void clickWysiwygUnorderedListButton()
    {
        getSkinExecutor().clickWysiwygUnorderedListButton();
    }

    public void clickWysiwygOrderedListButton()
    {
        getSkinExecutor().clickWysiwygOrderedListButton();
    }

    public void clickWysiwygIndentButton()
    {
        getSkinExecutor().clickWysiwygIndentButton();
    }

    public void clickWysiwygOutdentButton()
    {
        getSkinExecutor().clickWysiwygOutdentButton();
    }

    public void clickWikiBoldButton()
    {
        getSkinExecutor().clickWikiBoldButton();
    }

    public void clickWikiItalicsButton()
    {
        getSkinExecutor().clickWikiItalicsButton();
    }

    public void clickWikiUnderlineButton()
    {
        getSkinExecutor().clickWikiUnderlineButton();
    }

    public void clickWikiLinkButton()
    {
        getSkinExecutor().clickWikiLinkButton();
    }

    public void clickWikiHRButton()
    {
        getSkinExecutor().clickWikiHRButton();
    }

    public void clickWikiImageButton()
    {
        getSkinExecutor().clickWikiImageButton();
    }

    public void clickWikiSignatureButton()
    {
        getSkinExecutor().clickWikiSignatureButton();
    }

    public void assertWikiTextGeneratedByWysiwyg(String text)
    {
        getSkinExecutor().assertWikiTextGeneratedByWysiwyg(text);
    }

    public void assertHTMLGeneratedByWysiwyg(String xpath) throws Exception
    {
        getSkinExecutor().assertHTMLGeneratedByWysiwyg(xpath);
    }

    public void assertGeneratedHTML(String xpath) throws Exception
    {
        getSkinExecutor().assertGeneratedHTML(xpath);
    }

    public void openAdministrationPage()
    {
        getSkinExecutor().openAdministrationPage();
    }

    public void openAdministrationSection(String section)
    {
        getSkinExecutor().openAdministrationSection(section);
    }

    public String getUrl(String space, String doc)
    {
        return getUrl(space, doc, "view");
    }

    public String getUrl(String space, String doc, String action)
    {
        return getUrl(space, doc, action, null);
    }

    public String getUrl(String space, String doc, String action, String queryString)
    {
        StringBuilder builder = new StringBuilder("/xwiki/bin/");
        builder.append(action);
        builder.append('/');
        builder.append(space);
        builder.append('/');
        builder.append(doc);

        boolean needToAddSecretToken = !("view".equals(action) || "register".equals(action));
        boolean needToAddQuery = queryString != null && queryString.length() > 0;
        if (needToAddSecretToken || needToAddQuery) {
            builder.append('?');
        }
        if (needToAddSecretToken) {
            builder.append("form_token=");
            builder.append(getSecretToken());
            builder.append('&');
        }
        if (needToAddQuery) {
            builder.append(queryString);
        }
        return builder.toString();
    }

    public void pressKeyboardShortcut(String shortcut, boolean withCtrlModifier, boolean withAltModifier,
        boolean withShiftModifier) throws InterruptedException
    {
        getSkinExecutor().pressKeyboardShortcut(shortcut, withCtrlModifier, withAltModifier, withShiftModifier);
    }

    /**
     * Set global xwiki configuration options (as if the xwiki.cfg file had been modified). This is useful for testing
     * configuration options.
     * 
     * @param configuration the configuration in {@link Properties} format. For example "param1=value2\nparam2=value2"
     * @throws IOException if an error occurs while parsing the configuration
     */
    public void setXWikiConfiguration(String configuration) throws IOException
    {
        Properties properties = new Properties();
        properties.load(new ByteArrayInputStream(configuration.getBytes()));
        StringBuffer sb = new StringBuffer();

        // Since we don't have access to the XWiki object from Selenium tests and since we don't want to restart XWiki
        // with a different xwiki.cfg file for each test that requires a configuration change, we use the following
        // trick: We create a document and we access the XWiki object with a Velocity script inside that document.
        for (Entry<Object, Object> param : properties.entrySet()) {
            sb.append("$xwiki.xWiki.config.setProperty('").append(param.getKey()).append("', '")
                .append(param.getValue()).append("')").append('\n');
        }
        editInWikiEditor("Test", "XWikiConfigurationPageForTest", "xwiki/1.0");
        setFieldValue("content", sb.toString());
        // We can execute the script in preview mode. Thus we don't need to save the document.
        clickEditPreview();
    }

    @Override
    public boolean copyPage(String spaceName, String pageName, String targetSpaceName, String targetPageName)
    {
        return getSkinExecutor().copyPage(spaceName, pageName, targetSpaceName, targetPageName);
    }

    /**
     * Waits for the specified live table to load.
     * 
     * @param id the live table id
     */
    public void waitForLiveTable(String id)
    {
        waitForElement("//*[@id = '" + id + "-ajax-loader' and @class = 'xwiki-livetable-loader hidden']");
    }

    /**
     * (Re)-cache the secret token used for CSRF protection. A user with edit rights on Main.WebHome must be logged in.
     * This method must be called before {@link #getSecretToken()} is called and after each re-login.
     * 
     * @since 3.2M1
     * @see #getSecretToken()
     */
    public void recacheSecretToken()
    {
        // the registration form uses secret token
        open("XWiki", "Register", "register");
        waitPage();
        AbstractXWikiTestCase.secretToken = getSelenium().getValue("//input[@name='form_token']");
        if (AbstractXWikiTestCase.secretToken == null || AbstractXWikiTestCase.secretToken.length() <= 0) {
            // something is really wrong if this happens
            System.out.println("Warning: Failed to cache anti-CSRF secret token, some tests might fail!");
        }
        // return to the previous page
        getSelenium().goBack();
        if (!getSelenium().getLocation().contains(BASEDIR)) {
            // avoid returning to selenium start page (waitPage() doesn't handle that well)
            open("Main", "WebHome");
        }
        waitPage();
    }

    /**
     * Get the secret token used for CSRF protection. Remember to call {@link #recacheSecretToken()} first.
     * 
     * @return anti-CSRF secret token, or empty string if the token is not cached
     * @since 3.2M1
     * @see #recacheSecretToken()
     */
    public String getSecretToken()
    {
        if (AbstractXWikiTestCase.secretToken == null) {
            System.out.println("Warning: No cached anti-CSRF token found. "
                + "Make sure to call recacheSecretToken() before getSecretToken(), otherwise this test might fail.");
            return "";
        }
        return AbstractXWikiTestCase.secretToken;
    }

    /**
     * Drags and drops the source element on top of the target element.
     * 
     * @param sourceLocator locates the element to be dragged
     * @param targetLocator locates the element where to drop the dragged element
     */
    public void dragAndDrop(By sourceLocator, By targetLocator)
    {
        // Selenium#dragAndDropToObject(source, target) is not implemented over WebDriver.
        WebDriver driver = getDriver();
        WebElement source = driver.findElement(sourceLocator);
        WebElement target = driver.findElement(targetLocator);
        // Don't click in the middle of the element because it can contain a link.
        new Actions(getDriver()).moveToElement(source, 1, 1).clickAndHold().release(target).perform();
    }
}
