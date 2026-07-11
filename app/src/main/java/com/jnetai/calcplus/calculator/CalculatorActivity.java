package com.jnetai.calcplus.calculator;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.jnetai.calcplus.R;
import com.jnetai.calcplus.settings.SettingsActivity;
import com.jnetai.calcplus.util.CryptoUtils;
import com.jnetai.calcplus.util.SecurePrefs;
import com.jnetai.calcplus.util.VaultManager;
import com.jnetai.calcplus.vault.VaultActivity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

public class CalculatorActivity extends AppCompatActivity {
    private static final String TAG = "CalcPlus_Calc";
    private static final String DEFAULT_PIN = "12345678";

    private TextView displayText;
    private TextView expressionText;
    private StringBuilder currentInput = new StringBuilder();
    private StringBuilder expression = new StringBuilder();
    private String currentOperator = "";
    private double firstOperand = 0;
    private double secondOperand = 0;
    private boolean operatorPressed = false;
    private boolean equalsPressed = false;
    private boolean decimalUsed = false;
    private boolean vaultMode = false;
    private StringBuilder pinBuffer = new StringBuilder();
    private SecurePrefs securePrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calculator);
        securePrefs = SecurePrefs.getInstance(this);

        displayText = findViewById(R.id.displayText);
        expressionText = findViewById(R.id.expressionText);

        setupButtons();
        checkSelfDestruct();

        if (securePrefs.isDefaultPin()) {
            initializeDefaultPin();
        }
    }

    private void initializeDefaultPin() {
        try {
            String salt = CryptoUtils.generateSalt();
            String hash = CryptoUtils.hashPin(DEFAULT_PIN, salt);
            if (hash != null) {
                securePrefs.setSalt(salt);
                securePrefs.setPinHash(hash);
                securePrefs.setIsDefaultPin(true);
                Log.i(TAG, "INFO_CALC_001: Default PIN initialized");
            } else {
                Log.e(TAG, "ERR_CALC_001: Failed to hash default PIN");
            }
        } catch (Exception e) {
            Log.e(TAG, "ERR_CALC_002: Failed to initialize default PIN", e);
        }
    }

    private void checkSelfDestruct() {
        if (securePrefs.isSelfDestructed()) {
            Toast.makeText(this, R.string.self_destruct_triggered, Toast.LENGTH_LONG).show();
            Log.w(TAG, "WARN_CALC_001: Self-destruct flag is set");
        }
    }

    private void setupButtons() {
        int[] numberIds = {
                R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
                R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9
        };
        for (int id : numberIds) {
            Button btn = findViewById(id);
            btn.setOnClickListener(v -> onNumberClick(((Button) v).getText().toString()));
        }

        findViewById(R.id.btnAdd).setOnClickListener(v -> onOperatorClick("+"));
        findViewById(R.id.btnSubtract).setOnClickListener(v -> onOperatorClick("-"));
        findViewById(R.id.btnMultiply).setOnClickListener(v -> onOperatorClick("×"));
        findViewById(R.id.btnDivide).setOnClickListener(v -> onOperatorClick("÷"));
        findViewById(R.id.btnPercent).setOnClickListener(v -> onPercentClick());
        findViewById(R.id.btnDecimal).setOnClickListener(v -> onDecimalClick());
        findViewById(R.id.btnEquals).setOnClickListener(v -> onEqualsClick());
        findViewById(R.id.btnClear).setOnClickListener(v -> onClearClick());
        findViewById(R.id.btnDelete).setOnClickListener(v -> onDeleteClick());
        findViewById(R.id.btnPlusMinus).setOnClickListener(v -> onPlusMinusClick());
    }

    private void onNumberClick(String number) {
        if (vaultMode) {
            if (pinBuffer.length() < 16) {
                pinBuffer.append(number);
                displayText.setText(maskPin(pinBuffer.toString()));
            }
            return;
        }

        if (equalsPressed) {
            currentInput.setLength(0);
            expression.setLength(0);
            equalsPressed = false;
            decimalUsed = false;
        }

        if (currentInput.toString().equals("0") && !number.equals(".")) {
            currentInput.setLength(0);
        }

        if (currentInput.length() >= 15) return;

        currentInput.append(number);
        updateDisplay();
    }

    private void onOperatorClick(String op) {
        if (vaultMode) return;

        if (currentInput.length() == 0 && expression.length() > 0) {
            if (expression.charAt(expression.length() - 1) == ' ') {
                expression.setLength(expression.length() - 3);
            }
            expression.append(" ").append(op).append(" ");
            expressionText.setText(expression.toString());
            currentOperator = op;
            return;
        }

        if (currentInput.length() == 0) return;

        if (operatorPressed && !equalsPressed) {
            calculateResult();
        }

        firstOperand = Double.parseDouble(currentInput.toString());
        currentOperator = op;
        operatorPressed = true;
        equalsPressed = false;
        decimalUsed = false;

        expression.append(currentInput.toString()).append(" ").append(op).append(" ");
        expressionText.setText(expression.toString());
        currentInput.setLength(0);
    }

    private void onEqualsClick() {
        if (vaultMode) {
            attemptVaultAccess();
            return;
        }

        if (currentInput.length() == 0 || currentOperator.isEmpty()) return;

        secondOperand = Double.parseDouble(currentInput.toString());
        expression.append(currentInput.toString());
        expressionText.setText(expression.toString());

        calculateResult();
        equalsPressed = true;
        operatorPressed = false;
        currentOperator = "";
        decimalUsed = currentInput.toString().contains(".");
    }

    private void calculateResult() {
        try {
            BigDecimal first = new BigDecimal(String.valueOf(firstOperand));
            BigDecimal second = new BigDecimal(String.valueOf(secondOperand));
            BigDecimal result = BigDecimal.ZERO;

            switch (currentOperator) {
                case "+":
                    result = first.add(second);
                    break;
                case "-":
                    result = first.subtract(second);
                    break;
                case "×":
                    result = first.multiply(second);
                    break;
                case "÷":
                    if (second.compareTo(BigDecimal.ZERO) == 0) {
                        displayText.setText("Error");
                        Log.w(TAG, "WARN_CALC_002: Division by zero attempted");
                        return;
                    }
                    result = first.divide(second, 10, RoundingMode.HALF_UP);
                    break;
            }

            String resultStr = formatResult(result);
            currentInput.setLength(0);
            currentInput.append(resultStr);
            firstOperand = Double.parseDouble(resultStr);
            updateDisplay();
        } catch (Exception e) {
            displayText.setText("Error");
            Log.e(TAG, "ERR_CALC_003: Calculation error", e);
        }
    }

    private String formatResult(BigDecimal value) {
        if (value.compareTo(new BigDecimal("1E15")) >= 0 || value.compareTo(new BigDecimal("1E-10")) <= 0 && value.compareTo(BigDecimal.ZERO) != 0) {
            return new DecimalFormat("0.######E0").format(value);
        }
        BigDecimal stripped = value.stripTrailingZeros();
        String str = stripped.toPlainString();
        if (str.length() > 15) {
            str = str.substring(0, 15);
        }
        return str;
    }

    private void onClearClick() {
        if (vaultMode) {
            pinBuffer.setLength(0);
            displayText.setText("0");
            return;
        }
        currentInput.setLength(0);
        currentInput.append("0");
        expression.setLength(0);
        currentOperator = "";
        firstOperand = 0;
        secondOperand = 0;
        operatorPressed = false;
        equalsPressed = false;
        decimalUsed = false;
        updateDisplay();
    }

    private void onDeleteClick() {
        if (vaultMode) {
            if (pinBuffer.length() > 0) {
                pinBuffer.setLength(pinBuffer.length() - 1);
                displayText.setText(pinBuffer.length() > 0 ? maskPin(pinBuffer.toString()) : "0");
            }
            return;
        }
        if (currentInput.length() > 0) {
            char last = currentInput.charAt(currentInput.length() - 1);
            if (last == '.') decimalUsed = false;
            currentInput.setLength(currentInput.length() - 1);
            if (currentInput.length() == 0) {
                currentInput.append("0");
            }
            updateDisplay();
        }
    }

    private void onDecimalClick() {
        if (vaultMode) return;
        if (decimalUsed) return;
        if (currentInput.length() == 0) {
            currentInput.append("0");
        }
        currentInput.append(".");
        decimalUsed = true;
        updateDisplay();
    }

    private void onPercentClick() {
        if (vaultMode) return;
        if (currentInput.length() == 0) return;
        try {
            double value = Double.parseDouble(currentInput.toString());
            value = value / 100.0;
            currentInput.setLength(0);
            currentInput.append(formatResult(new BigDecimal(String.valueOf(value))));
            updateDisplay();
        } catch (Exception e) {
            Log.e(TAG, "ERR_CALC_004: Percent calculation error", e);
        }
    }

    private void onPlusMinusClick() {
        if (vaultMode) return;
        if (currentInput.length() == 0 || currentInput.toString().equals("0")) return;
        if (currentInput.charAt(0) == '-') {
            currentInput.deleteCharAt(0);
        } else {
            currentInput.insert(0, '-');
        }
        updateDisplay();
    }

    private void updateDisplay() {
        String display = currentInput.toString();
        if (display.length() > 15) {
            display = display.substring(0, 15);
        }
        displayText.setText(display);
    }

    private String maskPin(String pin) {
        StringBuilder masked = new StringBuilder();
        for (int i = 0; i < pin.length(); i++) {
            masked.append("●");
        }
        return masked.toString();
    }

    private void attemptVaultAccess() {
        String enteredPin = pinBuffer.toString();
        if (enteredPin.isEmpty()) {
            toggleVaultMode();
            return;
        }

        if (securePrefs.isSelfDestructed()) {
            Toast.makeText(this, R.string.self_destruct_triggered, Toast.LENGTH_LONG).show();
            pinBuffer.setLength(0);
            displayText.setText("0");
            return;
        }

        String storedHash = securePrefs.getPinHash();
        String salt = securePrefs.getSalt();

        if (storedHash == null || salt == null) {
            Log.e(TAG, "ERR_CALC_005: No PIN hash or salt found");
            Toast.makeText(this, "Error: PIN not configured", Toast.LENGTH_SHORT).show();
            pinBuffer.setLength(0);
            displayText.setText("0");
            return;
        }

        String enteredHash = CryptoUtils.hashPin(enteredPin, salt);

        if (enteredHash != null && enteredHash.equals(storedHash)) {
            securePrefs.resetFailedAttempts();
            pinBuffer.setLength(0);
            displayText.setText("0");
            vaultMode = false;

            if (securePrefs.isDefaultPin()) {
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
            } else {
                Intent intent = new Intent(this, VaultActivity.class);
                intent.putExtra("pin", enteredPin);
                startActivity(intent);
            }
        } else {
            securePrefs.incrementFailedAttempts();
            int failedAttempts = securePrefs.getFailedAttempts();
            int maxAttempts = securePrefs.getMaxAttempts();

            if (failedAttempts >= maxAttempts) {
                securePrefs.setSelfDestructed(true);
                VaultManager vaultManager = new VaultManager(this);
                vaultManager.destroyAllData();
                securePrefs.clearAll();
                Toast.makeText(this, R.string.self_destruct_triggered, Toast.LENGTH_LONG).show();
                Log.w(TAG, "WARN_CALC_003: Self-destruct triggered after " + failedAttempts + " failed attempts");
            } else {
                int remaining = maxAttempts - failedAttempts;
                Toast.makeText(this, "Incorrect PIN. " + remaining + " attempts remaining", Toast.LENGTH_SHORT).show();
            }

            pinBuffer.setLength(0);
            displayText.setText("0");
        }
    }

    public void onACButtonClick(View view) {
        if (!vaultMode) {
            currentInput.setLength(0);
            currentInput.append("0");
            expression.setLength(0);
            currentOperator = "";
            firstOperand = 0;
            secondOperand = 0;
            operatorPressed = false;
            equalsPressed = false;
            decimalUsed = false;
            updateDisplay();
            expressionText.setText("");
        }
        toggleVaultMode();
    }

    private void toggleVaultMode() {
        vaultMode = !vaultMode;
        pinBuffer.setLength(0);
        if (vaultMode) {
            displayText.setText("0");
            expressionText.setText("");
            findViewById(R.id.btnEquals).setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(getColor(R.color.accent)));
        } else {
            currentInput.setLength(0);
            currentInput.append("0");
            expression.setLength(0);
            expressionText.setText("");
            updateDisplay();
            findViewById(R.id.btnEquals).setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(getColor(R.color.buttonEquals)));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (vaultMode) {
            vaultMode = false;
            pinBuffer.setLength(0);
            currentInput.setLength(0);
            currentInput.append("0");
            expression.setLength(0);
            expressionText.setText("");
            updateDisplay();
            findViewById(R.id.btnEquals).setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(getColor(R.color.buttonEquals)));
        }
    }
}
