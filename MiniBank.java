import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

// MINI BANK MANAGEMENT SYSTEM
// Save as: MiniBank.java
// Requires Java 17 or later.

public class MiniBank {

    enum AccountType {
        SAVINGS, CURRENT, FIXED_DEPOSIT
    }

    enum TransactionType {
        DEPOSIT, WITHDRAW, TRANSFER
    }

    record BankInfo(String name, String branch, String ifsc) {}

    static class BankException extends Exception {
        BankException(String message) {
            super(message);
        }
    }

    static class InvalidAmountException extends BankException {
        InvalidAmountException(String message) {
            super(message);
        }
    }

    static class InsufficientFundsException extends BankException {
        InsufficientFundsException(String message) {
            super(message);
        }
    }

    static class AccountNotFoundException extends BankException {
        AccountNotFoundException(String message) {
            super(message);
        }
    }

    interface Transactable {
        void deposit(double amount) throws BankException;
        void withdraw(double amount) throws BankException;
    }

    interface InterestBearing {
        double getInterestRate();

        default double calculateInterest(double balance) {
            return balance * getInterestRate() / 100;
        }
    }

    static class Customer implements Serializable, Cloneable {
        private String name;
        private String phone;
        private String address;

        Customer(String name, String phone, String address) {
            this.name = name;
            this.phone = phone;
            this.address = address;
        }

        public String getName() {
            return name;
        }

        public String getPhone() {
            return phone;
        }

        @Override
        public Customer clone() {
            try {
                return (Customer) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public String toString() {
            return "Name: " + name +
                    "\nPhone: " + phone +
                    "\nAddress: " + address;
        }
    }

    static abstract class Account implements
            Transactable, Serializable, Comparable<Account> {

        protected String accountNumber;
        protected Customer customer;
        protected double balance;
        protected AccountType type;

        Account(String accountNumber, Customer customer,
                double balance, AccountType type) {
            this.accountNumber = accountNumber;
            this.customer = customer;
            this.balance = balance;
            this.type = type;
        }

        public String getAccountNumber() {
            return accountNumber;
        }

        public double getBalance() {
            return balance;
        }

        public Customer getCustomer() {
            return customer;
        }

        public AccountType getType() {
            return type;
        }

        @Override
        public synchronized void deposit(double amount)
                throws BankException {
            if (amount <= 0) {
                throw new InvalidAmountException(
                        "Amount must be greater than zero.");
            }
            balance += amount;
        }

        @Override
        public abstract void withdraw(double amount)
                throws BankException;

        public abstract double getMinimumBalance();

        @Override
        public int compareTo(Account other) {
            return Double.compare(this.balance, other.balance);
        }

        @Override
        public String toString() {
            return "\nAccount Number: " + accountNumber +
                    "\nCustomer: " + customer.getName() +
                    "\nAccount Type: " + type +
                    "\nBalance: Rs. " + balance;
        }
    }

    static class SavingsAccount extends Account
            implements InterestBearing {

        SavingsAccount(String number, Customer customer,
                       double balance) {
            super(number, customer, balance, AccountType.SAVINGS);
        }

        @Override
        public synchronized void withdraw(double amount)
                throws BankException {
            if (amount <= 0) {
                throw new InvalidAmountException(
                        "Invalid withdrawal amount.");
            }
            if (balance - amount < 500) {
                throw new InsufficientFundsException(
                        "Minimum balance of Rs. 500 required.");
            }
            balance -= amount;
        }

        @Override
        public double getMinimumBalance() {
            return 500;
        }

        @Override
        public double getInterestRate() {
            return 4.0;
        }
    }

    static class CurrentAccount extends Account {
        private static final double OVERDRAFT_LIMIT = 5000;

        CurrentAccount(String number, Customer customer,
                       double balance) {
            super(number, customer, balance, AccountType.CURRENT);
        }

        @Override
        public synchronized void withdraw(double amount)
                throws BankException {
            if (amount <= 0) {
                throw new InvalidAmountException(
                        "Invalid withdrawal amount.");
            }
            if (balance - amount < -OVERDRAFT_LIMIT) {
                throw new InsufficientFundsException(
                        "Overdraft limit exceeded.");
            }
            balance -= amount;
        }

        @Override
        public double getMinimumBalance() {
            return -OVERDRAFT_LIMIT;
        }
    }

    static class FixedDepositAccount extends Account
            implements InterestBearing {

        FixedDepositAccount(String number, Customer customer,
                            double balance) {
            super(number, customer, balance,
                    AccountType.FIXED_DEPOSIT);
        }

        @Override
        public void withdraw(double amount) throws BankException {
            throw new BankException(
                    "Withdrawal not allowed from fixed deposit.");
        }

        @Override
        public double getMinimumBalance() {
            return 0;
        }

        @Override
        public double getInterestRate() {
            return 7.0;
        }
    }

    static class Transaction implements Serializable {
        private String accountNumber;
        private TransactionType type;
        private double amount;
        private LocalDateTime date;

        Transaction(String accountNumber, TransactionType type,
                    double amount) {
            this.accountNumber = accountNumber;
            this.type = type;
            this.amount = amount;
            this.date = LocalDateTime.now();
        }

        @Override
        public String toString() {
            return date.format(
                    DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss")) +
                    " | " + accountNumber +
                    " | " + type +
                    " | Rs. " + amount;
        }
    }

    static class Repository<T> {
        private Map<String, T> data = new HashMap<>();

        public void add(String key, T value) {
            data.put(key, value);
        }

        public T get(String key) {
            return data.get(key);
        }

        public Collection<T> getAll() {
            return data.values();
        }

        public void remove(String key) {
            data.remove(key);
        }

        public boolean contains(String key) {
            return data.containsKey(key);
        }
    }

    static class BankService {
        private Repository<Account> accounts = new Repository<>();
        private List<Transaction> transactions = new ArrayList<>();
        private int accountCounter = 1001;

        private final BankInfo bankInfo =
                new BankInfo("Mini Bank", "Changa Branch", "MINI0001001");

        public void openAccount(String name, String phone,
                                String address, double balance,
                                AccountType type) throws BankException {
            if (balance < 0) {
                throw new InvalidAmountException(
                        "Initial balance cannot be negative.");
            }

            String number = String.valueOf(accountCounter++);
            Customer customer = new Customer(name, phone, address);
            Account account;

            switch (type) {
                case SAVINGS -> account =
                        new SavingsAccount(number, customer, balance);
                case CURRENT -> account =
                        new CurrentAccount(number, customer, balance);
                case FIXED_DEPOSIT -> account =
                        new FixedDepositAccount(number, customer, balance);
                default -> throw new BankException("Invalid account type.");
            }

            accounts.add(number, account);
            System.out.println("Account created successfully!");
            System.out.println("Account Number: " + number);
        }

        private Account findAccount(String number)
                throws AccountNotFoundException {
            Account account = accounts.get(number);
            if (account == null) {
                throw new AccountNotFoundException("Account not found.");
            }
            return account;
        }

        public void deposit(String number, double amount)
                throws BankException {
            Account account = findAccount(number);
            account.deposit(amount);
            transactions.add(new Transaction(
                    number, TransactionType.DEPOSIT, amount));
            System.out.println("Deposit successful!");
        }

        public void withdraw(String number, double amount)
                throws BankException {
            Account account = findAccount(number);
            account.withdraw(amount);
            transactions.add(new Transaction(
                    number, TransactionType.WITHDRAW, amount));
            System.out.println("Withdrawal successful!");
        }

        public synchronized void transfer(String senderNumber,
                                           String receiverNumber,
                                           double amount)
                throws BankException {
            if (senderNumber.equals(receiverNumber)) {
                throw new BankException(
                        "Cannot transfer to the same account.");
            }

            Account sender = findAccount(senderNumber);
            Account receiver = findAccount(receiverNumber);
            sender.withdraw(amount);

            try {
                receiver.deposit(amount);
            } catch (BankException e) {
                sender.deposit(amount);
                throw e;
            }

            transactions.add(new Transaction(
                    senderNumber, TransactionType.TRANSFER, amount));
            transactions.add(new Transaction(
                    receiverNumber, TransactionType.DEPOSIT, amount));
            System.out.println("Transfer successful!");
        }

        public void showStatement(String number) throws BankException {
            Account account = findAccount(number);
            System.out.println("\n===== ACCOUNT STATEMENT =====");
            System.out.println(account);
            System.out.println("\n===== TRANSACTIONS =====");

            for (Transaction transaction : transactions) {
                if (transaction.accountNumber.equals(number)) {
                    System.out.println(transaction);
                }
            }
        }

        public void displayAccounts() {
            System.out.println("\n===== ALL ACCOUNTS =====");
            if (accounts.getAll().isEmpty()) {
                System.out.println("No accounts available.");
                return;
            }

            for (Account account : accounts.getAll()) {
                System.out.println(account);
                System.out.println("------------------------");
            }
        }

        public void sortAccounts() {
            List<Account> list =
                    new ArrayList<>(accounts.getAll());
            Collections.sort(list);

            System.out.println("\n===== SORTED ACCOUNTS =====");
            for (Account account : list) {
                System.out.println(account.getAccountNumber() +
                        " : Rs. " + account.getBalance());
            }
        }

        public void calculateInterest(String number)
                throws BankException {
            Account account = findAccount(number);

            if (account instanceof InterestBearing interest) {
                double amount =
                        interest.calculateInterest(account.getBalance());
                System.out.println("Yearly Interest: Rs. " + amount);
            } else {
                System.out.println(
                        "This account does not earn interest.");
            }
        }

        public void batchDeposits(Map<String, Double> deposits) {
            ExecutorService executor =
                    Executors.newFixedThreadPool(3);

            for (Map.Entry<String, Double> entry :
                    deposits.entrySet()) {
                executor.submit(() -> {
                    try {
                        deposit(entry.getKey(), entry.getValue());
                    } catch (BankException e) {
                        System.out.println(e.getMessage());
                    }
                });
            }

            executor.shutdown();
            try {
                executor.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("Batch deposits completed.");
        }

        public void saveData() {
            try {
                Files.createDirectories(Paths.get("data"));

                try (ObjectOutputStream output =
                             new ObjectOutputStream(
                                     Files.newOutputStream(
                                             Paths.get("data/accounts.dat")))) {
                    output.writeObject(
                            new ArrayList<>(accounts.getAll()));
                    output.writeObject(transactions);
                }

                System.out.println("Data saved successfully!");
            } catch (IOException e) {
                System.out.println(
                        "Error saving data: " + e.getMessage());
            }
        }

        @SuppressWarnings("unchecked")
        public void loadData() {
            Path path = Paths.get("data/accounts.dat");
            if (!Files.exists(path)) {
                return;
            }

            try (ObjectInputStream input =
                         new ObjectInputStream(
                                 Files.newInputStream(path))) {
                List<Account> savedAccounts =
                        (List<Account>) input.readObject();
                transactions =
                        (List<Transaction>) input.readObject();

                for (Account account : savedAccounts) {
                    accounts.add(account.getAccountNumber(), account);
                    try {
                        int number = Integer.parseInt(
                                account.getAccountNumber());
                        accountCounter = Math.max(
                                accountCounter, number + 1);
                    } catch (NumberFormatException ignored) {
                    }
                }

                System.out.println(
                        "Previous data loaded successfully!");
            } catch (IOException | ClassNotFoundException e) {
                System.out.println(
                        "Error loading data: " + e.getMessage());
            }
        }

        public void generateReport() {
            try {
                Files.createDirectories(Paths.get("data"));
                StringBuilder report = new StringBuilder();

                report.append("===== BANK REPORT =====\n");
                report.append(bankInfo).append("\n");
                report.append("Total Accounts: ")
                        .append(accounts.getAll().size()).append("\n");

                double totalBalance = 0;
                for (Account account : accounts.getAll()) {
                    totalBalance += account.getBalance();
                }

                report.append("Total Balance: Rs. ")
                        .append(totalBalance).append("\n");
                report.append("\n===== TRANSACTION HISTORY =====\n");

                for (Transaction transaction : transactions) {
                    report.append(transaction).append("\n");
                }

                Files.writeString(
                        Paths.get("data/report.txt"), report.toString());
                System.out.println("Report generated successfully!");
            } catch (IOException e) {
                System.out.println(
                        "Report error: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        BankService bank = new BankService();
        bank.loadData();

        int choice;

        do {
            System.out.println("\n==============================");
            System.out.println("       MINI BANK SYSTEM");
            System.out.println("==============================");
            System.out.println("1. Open Account");
            System.out.println("2. Deposit Money");
            System.out.println("3. Withdraw Money");
            System.out.println("4. Transfer Money");
            System.out.println("5. Account Statement");
            System.out.println("6. Display All Accounts");
            System.out.println("7. Calculate Interest");
            System.out.println("8. Sort Accounts");
            System.out.println("9. Generate Bank Report");
            System.out.println("10. Save Data");
            System.out.println("11. Exit");
            System.out.print("\nEnter your choice: ");

            try {
                choice = sc.nextInt();
                sc.nextLine();

                switch (choice) {
                    case 1 -> {
                        System.out.print("Enter Customer Name: ");
                        String name = sc.nextLine();

                        System.out.print("Enter Phone Number: ");
                        String phone = sc.nextLine();

                        System.out.print("Enter Address: ");
                        String address = sc.nextLine();

                        System.out.print("Enter Initial Balance: ");
                        double balance = sc.nextDouble();

                        System.out.println("1. Savings Account");
                        System.out.println("2. Current Account");
                        System.out.println("3. Fixed Deposit Account");
                        System.out.print("Select Account Type: ");
                        int type = sc.nextInt();

                        AccountType accountType = switch (type) {
                            case 1 -> AccountType.SAVINGS;
                            case 2 -> AccountType.CURRENT;
                            case 3 -> AccountType.FIXED_DEPOSIT;
                            default -> null;
                        };

                        if (accountType == null) {
                            System.out.println("Invalid account type.");
                            break;
                        }

                        bank.openAccount(name, phone, address,
                                balance, accountType);
                    }

                    case 2 -> {
                        System.out.print("Enter Account Number: ");
                        String number = sc.nextLine();
                        System.out.print("Enter Deposit Amount: ");
                        double amount = sc.nextDouble();
                        bank.deposit(number, amount);
                    }

                    case 3 -> {
                        System.out.print("Enter Account Number: ");
                        String number = sc.nextLine();
                        System.out.print("Enter Withdrawal Amount: ");
                        double amount = sc.nextDouble();
                        bank.withdraw(number, amount);
                    }

                    case 4 -> {
                        System.out.print(
                                "Enter Sender Account Number: ");
                        String sender = sc.nextLine();
                        System.out.print(
                                "Enter Receiver Account Number: ");
                        String receiver = sc.nextLine();
                        System.out.print("Enter Transfer Amount: ");
                        double amount = sc.nextDouble();
                        bank.transfer(sender, receiver, amount);
                    }

                    case 5 -> {
                        System.out.print("Enter Account Number: ");
                        String number = sc.nextLine();
                        bank.showStatement(number);
                    }

                    case 6 -> bank.displayAccounts();

                    case 7 -> {
                        System.out.print("Enter Account Number: ");
                        String number = sc.nextLine();
                        bank.calculateInterest(number);
                    }

                    case 8 -> bank.sortAccounts();
                    case 9 -> bank.generateReport();
                    case 10 -> bank.saveData();

                    case 11 -> {
                        bank.saveData();
                        System.out.println(
                                "Thank you for using Mini Bank!");
                    }

                    default -> System.out.println(
                            "Invalid choice. Try again.");
                }

            } catch (BankException e) {
                System.out.println("Bank Error: " + e.getMessage());
                choice = 0;
            } catch (InputMismatchException e) {
                System.out.println("Please enter a valid input.");
                sc.nextLine();
                choice = 0;
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
                choice = 0;
            }

        } while (choice != 11);

        sc.close();
    }
}
