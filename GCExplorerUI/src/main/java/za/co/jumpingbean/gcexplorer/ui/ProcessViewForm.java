/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package za.co.jumpingbean.gcexplorer.ui;

import java.io.IOException;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.StackedAreaChart;
import javafx.scene.chart.StackedBarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.converter.NumberStringConverter;

/**
 *
 * @author mark
 */
public class ProcessViewForm implements Initializable {

    @FXML
    private LineChart<Number, Number> chtEdenSpace;
    @FXML
    private LineChart<Number, Number> chtSurvivorSpace;
    @FXML
    private LineChart<Number, Number> chtOldGenSpace;
    @FXML
    private LineChart<Number, Number> chtPermGenSpace;
    @FXML
    private Button btnGenerateGarbageOptions;
    @FXML
    private TextArea lblSysInfo;
    @FXML
    private Label lblGCInfo;
    @FXML
    private TextArea txtGeneratorStatus;

    private final Main app;
    private final UUID procId;
    @FXML
    private StackedBarChart<String, Number> chtStackedBarTotalMemory;
    @FXML
    private CategoryAxis xAxisCategory;
    @FXML
    private HBox hBox;
    @FXML
    private GridPane gridPane;
    @FXML
    private StackedAreaChart<Number, Number> chtStackedAreaTotalMemory;

    public ProcessViewForm(Main app, UUID procId) {
        this.procId = procId;
        this.app = app;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        configureChart(chtEdenSpace);
        configureChart(chtSurvivorSpace);
        configureChart(chtOldGenSpace);
        configureChart(chtPermGenSpace);
        configureChart(chtStackedAreaTotalMemory);

        chtEdenSpace.setData(app.getProcessController().getEdenSeries(procId));
        chtSurvivorSpace.setData(app.getProcessController().getSurvivorSeries(procId));
        chtOldGenSpace.setData(app.getProcessController().getOldGenSeries(procId));
        chtPermGenSpace.setData(app.getProcessController().getPermGenSeries(procId));
        ((NumberAxis) chtStackedAreaTotalMemory.getYAxis()).setForceZeroInRange(true);
        chtStackedAreaTotalMemory.setData(app.getProcessController().getStackedAreaChartSeries(procId));

        this.xAxisCategory.setCategories(FXCollections.<String>observableArrayList(Arrays.asList("Total")));
        this.chtStackedBarTotalMemory.setData(app.getProcessController().getStackedBarChartSeries(procId));
        configureChart(chtStackedBarTotalMemory);
        HBox.setHgrow(gridPane, Priority.ALWAYS);
        ((NumberAxis) this.chtStackedBarTotalMemory.getYAxis()).setTickLabelFormatter(new NumberStringConverter(NumberFormat.getIntegerInstance()));

        this.updateYAxii(app.getUnits());
        btnGenerateGarbageOptions.setOnAction(this::showGarbageOptionsForm);

        app.getProcessController().addSystemInfoEventListener(procId, new ChangeListener<String>() {

            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
                if (oldValue == null || !oldValue.equals(newValue)) {
                    Platform.runLater(new Runnable() {

                        @Override
                        public void run() {
                            lblSysInfo.setText(newValue);
                        }
                    });
                }
            }

        });
        app.getProcessController().addGCInfoEventListener(procId, new ChangeListener<String>() {

            @Override
            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
                if (oldValue == null || !oldValue.equals(newValue)) {
                    Platform.runLater(new Runnable() {

                        @Override
                        public void run() {
                            lblGCInfo.setText(newValue);
                        }
                    });
                }
            }

        });
        lblSysInfo.setText("Updating...");
        lblGCInfo.setText("Updating...");
}

    private void showGarbageOptionsForm(ActionEvent e) {
        Stage stage = new Stage();
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("garbageGeneratorOptionsForm.fxml")
            );
            GarbageGenerationOptionsForm controller = new GarbageGenerationOptionsForm(app, procId, this);
            loader.setController(controller);
            Parent pane = loader.load();
            Scene scene = new Scene(pane);
            stage.setScene(scene);
            stage.initModality(Modality.WINDOW_MODAL);
            stage.initOwner(gridPane.getParent().getScene().getWindow());
            stage.initStyle(StageStyle.UTILITY);
            stage.show();
        } catch (IOException ex) {
            Logger.getLogger(ProcessViewForm.class.getName()).log(Level.SEVERE, null, ex);
        }

    }

    private void configureChart(XYChart chart) {
        chart.setAnimated(false);
        chart.getXAxis().setAnimated(false);
        chart.getXAxis().setAutoRanging(true);
        if (chart.getXAxis() instanceof NumberAxis) {
            ((NumberAxis) chart.getXAxis()).setForceZeroInRange(false);
            ((NumberAxis) chart.getXAxis()).setTickLabelFormatter(new NumberStringConverter(NumberFormat.getIntegerInstance()));
        }
        chart.getYAxis().setAnimated(false);
        chart.getYAxis().setAutoRanging(true);
        ((NumberAxis) chart.getYAxis()).setForceZeroInRange(false);
        ((NumberAxis) chart.getYAxis()).setTickLabelFormatter(new NumberStringConverter(new DecimalFormat("#,###,##0.000")));

    }

    void updateYAxii(Units unit) {
        chtEdenSpace.getYAxis().setLabel(unit.getName());
        chtSurvivorSpace.getYAxis().setLabel(unit.getName());
        chtOldGenSpace.getYAxis().setLabel(unit.getName());
        chtPermGenSpace.getYAxis().setLabel(unit.getName());
        chtStackedBarTotalMemory.getYAxis().setLabel(unit.getName());
        chtStackedAreaTotalMemory.getYAxis().setLabel(unit.getName());
    }

    void setGenStatus(String str) {
        this.txtGeneratorStatus.setText(str);
    }

}
