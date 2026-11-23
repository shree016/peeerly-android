package tech.peeerly.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RoutesTest {

    @Test
    public void ourSiteLoadsInsideTheApp() {
        assertTrue(Routes.isInternal("https://www.peeerly.tech/polls"));
        assertTrue(Routes.isInternal("https://peeerly.tech"));
        assertTrue(Routes.isInternal("https://WWW.PEEERLY.TECH/quiz"));
        assertTrue(Routes.isInternal("https://www.peeerly.tech/x?name=a|b c"));
    }

    @Test
    public void lookAlikeSitesNeverLoadInsideTheApp() {
        String[] bad = {
            "http://www.peeerly.tech/",
            "https://www.peeerly.tech.evil.com/",
            "https://evilpeeerly.tech/",
            "https://peerly.tech/",
            "https://www.peeerly.tech@evil.com/",
            "https://evil.com?@www.peeerly.tech",
            "https://evil.com#@www.peeerly.tech",
            "https://evil.com\\@www.peeerly.tech",
            "https://user@www.peeerly.tech/",
            "https://www.peeerly.tech:8443/",
            "javascript:alert(1)",
            "intent://scan/#Intent;end",
            "file:///data/data/tech.peeerly.app",
            "",
            null,
        };
        for (String url : bad) assertFalse(String.valueOf(url), Routes.isInternal(url));
    }

    @Test
    public void googleLoginIsRecognisedExactly() {
        assertTrue(Routes.isGoogleLogin("https://accounts.google.com/o/oauth2/v2/auth?client_id=x"));
        assertFalse(Routes.isGoogleLogin("https://accounts.google.com.evil.com/o"));
        assertFalse(Routes.isGoogleLogin("http://accounts.google.com/o"));
    }

    @Test
    public void documentsAreDownloaded() {
        assertTrue(Routes.isDocument(
                "https://www.peeerly.tech/api/experiences/v1/documents?url=https://s3/a.pdf&name=JD.pdf"));
        assertFalse(Routes.isDocument("https://evil.com/api/experiences/v1/documents"));
    }

    @Test
    public void quizScreensBehaveDifferently() {
        assertTrue(Routes.isLivePlay("https://www.peeerly.tech/quiz/play/abc"));
        assertFalse(Routes.isLivePlay("https://www.peeerly.tech/quiz/play/abc/report"));
        assertFalse(Routes.isLivePlay("https://www.peeerly.tech/quiz/join"));
        assertTrue(Routes.keepsScreenOn("https://www.peeerly.tech/quiz/sessions/s1/present"));
        assertTrue(Routes.keepsScreenOn("https://www.peeerly.tech/polls/p1/wall"));
        assertFalse(Routes.keepsScreenOn("https://www.peeerly.tech/polls"));
    }

    @Test
    public void appStartsOnTheSitesFirstPage() {
        assertTrue(Routes.isInternal(Routes.ORIGIN + Routes.START));
        assertEquals("/", Routes.path(Routes.ORIGIN + Routes.START));
    }

    @Test
    public void loginCannotRedirectElsewhere() {
        assertEquals("/polls", Routes.afterLogin("/polls"));
        assertEquals("/dashboard", Routes.afterLogin(null));
        assertEquals("/dashboard", Routes.afterLogin("//evil.com"));
        assertEquals("/dashboard", Routes.afterLogin("https://evil.com"));
        assertEquals("/dashboard", Routes.afterLogin("/\\evil.com"));
    }
}
