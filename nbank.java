import java.util.*;

/* ===== ATM System with Multiple Accounts, Lockout, OTP, and Mini Statement ===== */
class BankAccount {
    private final int accountNumber;
    private String holderName;
    private int pin;
    private double balance;
    private boolean locked = false;
    private int failedAttempts = 0;
    private final Deque<String> transactions = new ArrayDeque<>(); // keep recent first
    private static final int MAX_ATTEMPTS = 3;
    private static final int HISTORY_LIMIT = 20;

    BankAccount(int accountNumber, String holderName, int pin, double openingBalance) {
        this.accountNumber = accountNumber;
        this.holderName = holderName;
        this.pin = pin;
        this.balance = openingBalance;
        addTxn("ACCOUNT CREATED | Opening Balance: " + openingBalance);
    }

    public int getAccountNumber() { return accountNumber; }
    public String getHolderName() { return holderName; }
    public boolean isLocked() { return locked; }
    public double getBalance() { return balance; }

    public boolean authenticate(int enteredPin) {
        if (locked) return false;
        if (enteredPin == pin) {
            failedAttempts = 0;
            return true;
        } else {
            failedAttempts++;
            if (failedAttempts >= MAX_ATTEMPTS) {
                locked = true;
            }
            return false;
        }
    }

    public int remainingAttempts() {
        return Math.max(0, MAX_ATTEMPTS - failedAttempts);
    }

    public void forceLock() { locked = true; }

    public boolean changePin(int oldPin, int newPin) {
        if (authenticate(oldPin)) {
            pin = newPin;
            addTxn("PIN CHANGED");
            return true;
        }
        return false;
    }

    public boolean deposit(double amount) {
        if (amount <= 0) return false;
        balance += amount;
        addTxn("DEPOSIT | +" + amount + " | Bal: " + balance);
        return true;
    }

    public boolean withdraw(double amount) {
        if (amount <= 0 || amount > balance) return false;
        balance -= amount;
        addTxn("WITHDRAW | -" + amount + " | Bal: " + balance);
        return true;
    }

    public List<String> getMiniStatement() {
        return new ArrayList<>(transactions);
    }

    private void addTxn(String line) {
        String entry = String.format("%s | %s", new Date(), line);
        transactions.addFirst(entry);
        while (transactions.size() > HISTORY_LIMIT) transactions.removeLast();
    }
}

/* ===== OTP Service (simple in-memory) ===== */
class OtpService {
    private final Random rnd = new Random();

    public int generateOtp() {
        // 6-digit OTP, leading zeros allowed
        return 100000 + rnd.nextInt(900000);
    }

    public boolean verifyOtp(Scanner sc, int otp) {
        System.out.print("Enter OTP sent to your registered number/email: ");
        int entered = readIntSafe(sc);
        return entered == otp;
    }

    public static int readIntSafe(Scanner sc) {
        while (!sc.hasNextInt()) {
            System.out.print("Please enter a valid number: ");
            sc.next();
        }
        return sc.nextInt();
    }

    public static double readDoubleSafe(Scanner sc) {
        while (!sc.hasNextDouble()) {
            System.out.print("Please enter a valid amount: ");
            sc.next();
        }
        return sc.nextDouble();
    }
}

/* ===== Bank (manages multiple accounts) ===== */
class Bank {
    private final Map<Integer, BankAccount> accounts = new HashMap<>();
    private final Scanner sc;
    private final OtpService otpService = new OtpService();

    Bank(Scanner sc) {
        this.sc = sc;
    }

    public void createAccountsInteractive() {
        System.out.print("How many accounts would you like to create? ");
        int n = OtpService.readIntSafe(sc);
        sc.nextLine(); // consume newline
        for (int i = 1; i <= n; i++) {
            System.out.println("\n--- Create Account " + i + " ---");
            System.out.print("Enter unique Account Number (integer): ");
            int accNo = OtpService.readIntSafe(sc);
            while (accounts.containsKey(accNo)) {
                System.out.print("Account number exists. Enter a different number: ");
                accNo = OtpService.readIntSafe(sc);
            }
            sc.nextLine(); // consume newline
            System.out.print("Enter Account Holder Name: ");
            String name = sc.nextLine().trim();
            System.out.print("Set a 4-digit PIN: ");
            int pin = OtpService.readIntSafe(sc);
            System.out.print("Enter Opening Balance: ");
            double bal = OtpService.readDoubleSafe(sc);
            accounts.put(accNo, new BankAccount(accNo, name, pin, bal));
            System.out.println("✅ Account created for " + name + " (A/C: " + accNo + ")");
        }
        if (n == 0) {
            // Provide a couple of demo accounts if user chose 0
            accounts.put(1001, new BankAccount(1001, "Alice", 1111, 50000));
            accounts.put(1002, new BankAccount(1002, "Bob", 2222, 75000));
            System.out.println("\nCreated demo accounts: 1001/1111 and 1002/2222");
        }
    }

    private BankAccount login() {
        System.out.println("\n===== LOGIN =====");
        System.out.print("Enter Account Number: ");
        int accNo = OtpService.readIntSafe(sc);
        BankAccount acc = accounts.get(accNo);
        if (acc == null) {
            System.out.println(" Account not found.");
            return null;
        }
        if (acc.isLocked()) {
            System.out.println(" Account is locked due to too many failed attempts. Contact support.");
            return null;
        }

        System.out.print("Enter PIN: ");
        int pin = OtpService.readIntSafe(sc);

        if (!acc.authenticate(pin)) {
            if (acc.isLocked()) {
                System.out.println(" Incorrect PIN. Attempts exceeded. Account locked.");
            } else {
                System.out.println("Incorrect PIN. Remaining attempts: " + acc.remainingAttempts());
            }
            return null;
        }

        // OTP verification before granting session
        int otp = otpService.generateOtp();
        // In a real system, this would be sent via SMS/Email. For demo, we show it.
        System.out.println("(Demo) Your OTP is: " + otp);
        if (!otpService.verifyOtp(sc, otp)) {
            System.out.println(" OTP verification failed.");
            return null;
        }

        System.out.println(" Login successful. Welcome, " + acc.getHolderName() + "!");
        return acc;
    }

    private void session(BankAccount acc) {
        while (true) {
            System.out.println("\n====== ATM MENU ======");
            System.out.println("1. Check Balance");
            System.out.println("2. Deposit (OTP)");
            System.out.println("3. Withdraw (OTP)");
            System.out.println("4. Mini Statement");
            System.out.println("5. Change PIN (OTP)");
            System.out.println("6. Logout");
            System.out.print("Choose an option: ");

            int choice = OtpService.readIntSafe(sc);

            switch (choice) {
                case 1:
                    System.out.println(" Balance: " + acc.getBalance());
                    break;

                case 2: {
                    System.out.print("Enter deposit amount: ");
                    double amt = OtpService.readDoubleSafe(sc);
                    int otp = otpService.generateOtp();
                    System.out.println("(Demo) Your OTP is: " + otp);
                    if (!otpService.verifyOtp(sc, otp)) {
                        System.out.println("OTP failed. Deposit cancelled.");
                        break;
                    }
                    if (acc.deposit(amt)) {
                        System.out.println(" Deposited " + amt + ". New balance: " + acc.getBalance());
                    } else {
                        System.out.println(" Invalid amount.");
                    }
                    break;
                }

                case 3: {
                    System.out.print("Enter withdraw amount: ");
                    double amt = OtpService.readDoubleSafe(sc);
                    if (amt > acc.getBalance()) {
                        System.out.println(" Insufficient balance.");
                        break;
                    }
                    int otp = otpService.generateOtp();
                    System.out.println("(Demo) Your OTP is: " + otp);
                    if (!otpService.verifyOtp(sc, otp)) {
                        System.out.println(" OTP failed. Withdrawal cancelled.");
                        break;
                    }
                    if (acc.withdraw(amt)) {
                        System.out.println("✅ Withdrawn " + amt + ". New balance: " + acc.getBalance());
                    } else {
                        System.out.println(" Invalid amount.");
                    }
                    break;
                }

                case 4: {
                    System.out.println("\n--- MINI STATEMENT (Last " + 20 + ") ---");
                    List<String> hist = acc.getMiniStatement();
                    if (hist.isEmpty()) {
                        System.out.println("No transactions yet.");
                    } else {
                        for (String line : hist) System.out.println(line);
                    }
                    break;
                }

                case 5: {
                    System.out.print("Enter current PIN: ");
                    int oldPin = OtpService.readIntSafe(sc);
                    System.out.print("Enter new PIN: ");
                    int newPin = OtpService.readIntSafe(sc);

                    int otp = otpService.generateOtp();
                    System.out.println("(Demo) Your OTP is: " + otp);
                    if (!otpService.verifyOtp(sc, otp)) {
                        System.out.println(" OTP failed. PIN not changed.");
                        break;
                    }

                    if (acc.changePin(oldPin, newPin)) {
                        System.out.println("PIN changed successfully.");
                    } else {
                        System.out.println(" Incorrect current PIN.");
                    }
                    break;
                }

                case 6:
                    System.out.println(" Logged out.");
                    return;

                default:
                    System.out.println(" Invalid option. Try again.");
            }
        }
    }

    public void run() {
        while (true) {
            System.out.println("\n====== WELCOME TO ATM ======");
            System.out.println("1. Login");
            System.out.println("2. Exit");
            System.out.print("Choose an option: ");
            int ch = OtpService.readIntSafe(sc);

            switch (ch) {
                case 1: {
                    BankAccount acc = login();
                    if (acc != null) session(acc);
                    break;
                }
                case 2:
                    System.out.println(" Thanks for using the ATM. Goodbye!");
                    return;
                default:
                    System.out.println(" Invalid option.");
            }
        }
    }
}

/* ===== Main ===== */
public class nbank {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        Bank bank = new Bank(sc);
        bank.createAccountsInteractive(); // ask user how many & create
        bank.run();
        sc.close();
    }
}
