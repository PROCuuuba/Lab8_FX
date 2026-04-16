module com.example.lab8_FX {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.desktop;

    opens com.example.lab8_FX to javafx.fxml;
    exports com.example.lab8_FX;
}