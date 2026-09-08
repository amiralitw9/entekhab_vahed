import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.awt.*;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Scanner;

public class Main {

    // ---------- class-level (static) fields ----------
    static boolean type = false; // true: register at start time; false: wait for capacity

    static Scanner scan = new Scanner(System.in);
    static ArrayList<Thread> threads = new ArrayList<>();

    static Thread nextCourse;
    static long startRegTime;
    static long testTime = 15000;

    static long timeMargin;

    static int tabNum;
    static int nextTab;
    static LocalTime startTime = LocalTime.of(8, 0, 0);

    // ---------- main method ----------
    public static void main(String[] args) {
        // If you use Selenium 4.6+, you can remove this setProperty and let Selenium Manager handle it.
        // System.setProperty("webdriver.edge.driver", "C:\\path\\to\\msedgedriver.exe");

        String STUDENT_ID = "40000000";
        String EDU_PASS   = "password";

        if (type) {
            System.out.println("I am in registration mode at moment " + startTime);
            timeMargin = 0;
            setStartRegTime(STUDENT_ID, EDU_PASS);

            threads.add(new TestCourse(STUDENT_ID, EDU_PASS, 6));

            threads.add(new RegisterCourse(STUDENT_ID, EDU_PASS, 9, threads.getFirst(), 0));
            threads.add(new RegisterCourse(STUDENT_ID, EDU_PASS, 3, threads.getFirst(), 300));
            threads.add(new RegisterCourse(STUDENT_ID, EDU_PASS, 1, threads.getFirst(), 700));

            tabNum = threads.size();
            nextTab = 2;
            nextCourse = threads.get(1);
            threads.getFirst().start();

        } else {
            threads.add(new RegOnCap(STUDENT_ID, EDU_PASS, 12));
            tabNum = threads.size();
            nextTab = 2;
            threads.getFirst().start();
        }
    }

    // ---------- helpers and threads ----------
    public static WebDriver logIn(String studentID, String pass) {
        WebDriver webDriver = new EdgeDriver();
        webDriver.get("http://my.edu.sharif.edu");
        webDriver.manage().window().maximize();

        WebElement usernameM = waitToFindByName(webDriver, "username", 100, 25);
        WebElement passwordM = webDriver.findElement(By.name("password"));
        usernameM.sendKeys(studentID);
        passwordM.sendKeys(pass);

        // wait for Enter in console to continue
        scan.nextLine();

        if (nextTab < tabNum) {
            nextCourse.start();
            nextCourse = threads.get(nextTab);
            nextTab += 1;
        } else if (nextTab == tabNum) {
            nextCourse.start();
            nextTab += 1;
        }

        WebElement logButton = webDriver.findElement(By.xpath("//button[@class='ui large fluid primary button']"));
        logButton.click();

        WebElement markButton = waitToFind(webDriver, "//a[@href='/courses/marked']", 100, 25);
        markButton.click();

        waitToFind(webDriver, "//*[@id='root']/div/div[2]/table/tbody/tr[1]/td[1]/button[1]", 100, 25);
        try { Thread.sleep(1000); } catch (InterruptedException e) { throw new RuntimeException(e); }

        webDriver.navigate().refresh();
        return webDriver;
    }

    public static WebDriver logInForCapMode(String studentID, String pass) {
        WebDriver webDriver = new EdgeDriver();
        webDriver.get("http://my.edu.sharif.edu");
        webDriver.manage().window().maximize();

        WebElement usernameM = waitToFindByName(webDriver, "username", 100, 25);
        WebElement passwordM = webDriver.findElement(By.name("password"));
        usernameM.sendKeys(studentID);
        passwordM.sendKeys(pass);

        scan.nextLine();
        if (nextTab <= tabNum - 1) {
            nextCourse.start();
            nextCourse = threads.get(nextTab);
            nextTab += 1;
        } else if (nextTab == tabNum) {
            nextCourse.start();
            nextTab += 1;
        }

        WebElement logButton = webDriver.findElement(By.xpath("//button[@class='ui large fluid primary button']"));
        logButton.click();

        WebElement markButton = waitToFind(webDriver, "//a[@href='/courses/marked']", 100, 25);
        markButton.click();

        webDriver.navigate().refresh();
        return webDriver;
    }

    public static void setStartRegTime(String studentID, String pass) {
        WebDriver clockDriver = new EdgeDriver();
        clockDriver.manage().window().maximize();
        clockDriver.get("http://edu.sharif.edu");

        WebElement username = waitToFindByName(clockDriver, "username", 100, 25);
        WebElement password = clockDriver.findElement(By.name("password"));
        username.sendKeys(studentID);
        password.sendKeys(pass);

        WebElement logButton = clockDriver.findElement(By.xpath("//*[@id='loginform']/div/form/div[3]/button"));
        logButton.click();

        long clockDelay = -System.currentTimeMillis();
        clockDriver.navigate().refresh();

        WebElement timeElement = waitToFind(clockDriver, "//*[@id='currentClock']", 100, 5);
        clockDelay += System.currentTimeMillis();

        String lastCheck = timeElement.getAttribute("innerHTML");

        long clockRead = -System.currentTimeMillis();
        while (lastCheck.equals(timeElement.getAttribute("innerHTML"))) {
            clockRead = -System.currentTimeMillis();
            timeElement = clockDriver.findElement(By.xpath("//*[@id='currentClock']"));
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("H:mm:ss");
        LocalTime nowTime = LocalTime.parse(timeElement.getAttribute("innerHTML"), formatter);
        clockRead += System.currentTimeMillis();

        long diff = nowTime.until(startTime, ChronoUnit.SECONDS);
        startRegTime = System.currentTimeMillis() + diff * 1000 - clockDelay - clockRead + timeMargin;

        clockDriver.close();
        System.out.println("diff =" + diff);
        System.out.println("clockDelay =" + clockDelay);
        System.out.println("clockRead =" + clockRead);
    }

    static class RegisterCourse extends Thread {
        String studentID;
        String pass;
        int courseNum;
        WebDriver webDriver;
        Thread testCourse;
        long dd;

        RegisterCourse(String studentID, String pass, int courseNum, Thread testCourse, long dd) {
            this.studentID = studentID;
            this.pass = pass;
            this.courseNum = courseNum;
            this.testCourse = testCourse;
            this.dd = dd;
        }

        @Override
        public void run() {
            webDriver = logIn(studentID, pass);
            openPlus(webDriver, courseNum);
            WebElement button = registerCourseButton(webDriver);

            try { testCourse.join(); } catch (InterruptedException e) {
                System.out.println("Ohhh nooo " + courseNum);
                throw new RuntimeException(e);
            }

            try { Thread.sleep(dd); } catch (InterruptedException e) { throw new RuntimeException(e); }
            button.click();
        }
    }

    static class RegOnCap extends Thread {
        String studentID;
        String pass;
        int courseNum;
        WebDriver webDriver;
        Thread bebPlayer;

        RegOnCap(String studentID, String pass, int courseNum) {
            this.studentID = studentID;
            this.pass = pass;
            this.courseNum = courseNum;
        }

        @Override
        public void run() {
            webDriver = logInForCapMode(studentID, pass);
            while (true) {
                WebElement colorBara = waitToFind(webDriver,
                        "//*[@id='root']/div/div[2]/table/tbody/tr[" + courseNum + "]/td[8]", 100, 25);
                String text = colorBara.getAttribute("innerHTML");
                if (text.contains("blue") || text.contains("yellow")) {
                    WebElement plusButton = webDriver.findElement(By.xpath(
                            "//*[@id='root']/div/div[2]/table/tbody/tr[" + courseNum + "]/td[1]/button[1]"));
                    plusButton.click();
                    try { Thread.sleep(300); } catch (InterruptedException e) { throw new RuntimeException(e); }

                    WebElement registerButton = waitToFind(webDriver,
                            "//button[@class='ui icon positive right labeled button']", 100, 25);
                    try {
                        do {
                            registerButton.click();
                            Thread.sleep(500);
                        } while (registerButton.isDisplayed());
                    } catch (Exception ignored) {}

                    System.out.println("I applied for the course with index " + courseNum + ". Please check it yourself and then run me again");

                    bebPlayer = new BebPlayer();
                    bebPlayer.start();
                    break;
                } else {
                    webDriver.navigate().refresh();
                }
            }
        }
    }

    static class TestCourse extends Thread {
        String studentID;
        String pass;
        int courseNum;
        WebDriver webDriver;

        TestCourse(String studentID, String pass, int courseNum) {
            this.studentID = studentID;
            this.pass = pass;
            this.courseNum = courseNum;
        }

        @Override
        public void run() {
            webDriver = logIn(studentID, pass);
            openPlus(webDriver, courseNum);
            WebElement button = registerCourseButton(webDriver);

            WebDriverWait wait = new WebDriverWait(webDriver, Duration.ofSeconds(5));
            WebElement popup = webDriver.findElement(By.xpath("/html/body/div[2]/div"));

            long nowTime = System.currentTimeMillis();
            long diff = Main.startRegTime - nowTime;

            try { Thread.sleep(diff - Main.testTime); } catch (InterruptedException e) { throw new RuntimeException(e); }

            long clickDelay = -System.currentTimeMillis();
            button.click();
            clickDelay += System.currentTimeMillis();

            long ping = -System.currentTimeMillis();
            try {
                wait.until(ExpectedConditions.invisibilityOf(popup));
                ping += System.currentTimeMillis();
            } catch (Exception e) {
                ping = 10;
            }

            System.out.println("Ping = " + ping);
            System.out.println("ClickDelay = " + clickDelay);

            try { Thread.sleep(Main.testTime - clickDelay - ping / 2); } catch (InterruptedException e) { throw new RuntimeException(e); }
        }
    }

    static class BebPlayer extends Thread {
        public void run() {
            while (true) {
                Toolkit.getDefaultToolkit().beep();
            }
        }
    }

    static class ChangeGroup extends Thread {
        String studentID;
        String pass;
        int courseNum;
        int changeToNum;
        WebDriver webDriver;
        Thread testCourse;

        ChangeGroup(String studentID, String pass, int courseNum, int changeToNum, Thread testCourse) {
            this.studentID = studentID;
            this.pass = pass;
            this.courseNum = courseNum;
            this.changeToNum = changeToNum;
            this.testCourse = testCourse;
        }

        @Override
        public void run() {
            webDriver = logIn(studentID, pass);
            openPlus(webDriver, courseNum);
            WebElement button = changeGroupButton(webDriver, changeToNum);

            try { testCourse.join(); } catch (InterruptedException e) { throw new RuntimeException(e); }
            button.click();
        }
    }

    static class DeleteCourse extends Thread {
        String studentID;
        String pass;
        int courseNum;
        WebDriver webDriver;
        Thread testCourse;

        DeleteCourse(String studentID, String pass, int courseNum, Thread testCourse) {
            this.studentID = studentID;
            this.pass = pass;
            this.courseNum = courseNum;
            this.testCourse = testCourse;
        }

        @Override
        public void run() {
            webDriver = logIn(studentID, pass);
            openPlus(webDriver, courseNum);
            WebElement button = deleteCourseButton(webDriver);

            try { testCourse.join(); } catch (InterruptedException e) { throw new RuntimeException(e); }
            button.click();
        }
    }

    public static void openPlus(WebDriver webDriver, int courseNum) {
        WebElement plusButton = waitToFind(webDriver,
                "//*[@id='root']/div/div[2]/table/tbody/tr[" + courseNum + "]/td[1]/button[1]", 100, 25);
        plusButton.click();
    }

    public static WebElement registerCourseButton(WebDriver webDriver) {
        return waitToFind(webDriver, "//button[@class='ui icon positive right labeled button']", 100, 25);
    }

    public static WebElement deleteCourseButton(WebDriver webDriver) {
        return waitToFind(webDriver, "//button[@class='ui icon negative right labeled button']", 100, 25);
    }

    public static WebElement changeGroupButton(WebDriver webDriver, int changeC) {
        WebElement changeRol = waitToFind(webDriver, "//div[@role='listbox']", 100, 25);
        changeRol.click();

        WebElement selectCourse = waitToFind(webDriver,
                "/html/body/div[2]/div/div[2]/table/tbody/tr[4]/td[2]/div/div[2]/div[" + changeC + "]", 10, 25);
        selectCourse.click();

        return webDriver.findElement(By.xpath("//button[@class='ui icon primary right labeled button']"));
    }

    public static WebElement waitToFind(WebDriver webDriver, String elementXpath, int timeOut, int pollTime) {
        Wait<WebDriver> wait = new FluentWait<>(webDriver)
                .withTimeout(Duration.ofSeconds(timeOut))
                .pollingEvery(Duration.ofMillis(pollTime))
                .ignoring(org.openqa.selenium.NoSuchElementException.class);

        return wait.until(d -> d.findElement(By.xpath(elementXpath)));
    }

    public static WebElement waitToFindByName(WebDriver webDriver, String elementName, int timeOut, int pollTime) {
        Wait<WebDriver> wait = new FluentWait<>(webDriver)
                .withTimeout(Duration.ofSeconds(timeOut))
                .pollingEvery(Duration.ofMillis(pollTime))
                .ignoring(org.openqa.selenium.NoSuchElementException.class);

        return wait.until(d -> d.findElement(By.name(elementName)));
    }
}
