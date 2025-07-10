import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.Alert.AlertType;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class myOrderController {
    @FXML
    public Button returnButton;
    @FXML
    private TableView<Order> myOrderTableView; // 表格显示订单
    @FXML
    private TableColumn<Order, String> nameTableColumn; // 菜名列
    @FXML
    private TableColumn<Order, Double> singlePriceTableColumn; // 单价列
    @FXML
    private TableColumn<Order, String> typeTableColumn; // 类型列
    @FXML
    private TableColumn<Order, Integer> numTableColumn; // 购买份数列
    @FXML
    private TableColumn<Order, String> dateTableColumn; // 日期列
    @FXML
    private TableColumn<Order, Double> priceTableColumn; // 总价列
    @FXML
    private Label sumPriceLabel; // 显示总价格的标签
    @FXML
    private Label balanceLabel; // 显示余额的标签
    @FXML
    private Button deleteButton; // 删除按钮

    private double totalPrice = 0.0; // 记录总价格

    @FXML
    public void initialize() throws Exception {
        // 设置表格列的单元格工厂
        nameTableColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        singlePriceTableColumn.setCellValueFactory(new PropertyValueFactory<>("price")); // 显示单价
        typeTableColumn.setCellValueFactory(new PropertyValueFactory<>("type"));
        numTableColumn.setCellValueFactory(new PropertyValueFactory<>("number"));
        priceTableColumn.setCellValueFactory(new PropertyValueFactory<>("totalPrice"));
        dateTableColumn.setCellValueFactory(new PropertyValueFactory<>("date"));
        // 加载订单数据
        loadData();

        // 设置余额显示
        balanceLabel.setText(String.format("%.2f元", MainOrdering.getBalance(MainOrdering.CurrentID, MainOrdering.CurrentType)));
    }

    private void loadData() {
        List<Order> orders = new ArrayList<>();
        int userId = MainOrdering.CurrentID; // 当前用户ID

        try {
            // 通过联结查询从 orders 和 dishes 表获取数据
            String sql = "SELECT o.ID, o.Number, d.Name, d.Price, d.Type, o.OrderDate " +
                    "FROM orders o JOIN dishes d ON o.DishID = d.ID " +
                    "WHERE o.UserID = ?";
            PreparedStatement preparedStatement = MainOrdering.connection.prepareStatement(sql);
            preparedStatement.setInt(1, userId);
            ResultSet resultSet = preparedStatement.executeQuery();

            while (resultSet.next()) {
                int id = resultSet.getInt("ID");
                String name = resultSet.getString("Name");
                double price = resultSet.getDouble("Price");
                String type = resultSet.getString("Type");
                int number = resultSet.getInt("Number");
                totalPrice += price * number; // 更新总价格
                String date = resultSet.getString("OrderDate"); // 加载日期数据

                // 将订单信息添加到列表
                orders.add(new Order(id, name, price, type, number, date));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 更新表格项和总价格
        myOrderTableView.getItems().addAll(orders);
        sumPriceLabel.setText(String.format("%.2f元", totalPrice));
    }

    // 删除订单
    @FXML
    private void handleDeleteButtonClicked() throws Exception {
        Order selectedOrder = myOrderTableView.getSelectionModel().getSelectedItem();
        if (selectedOrder != null) {
            // 创建确认对话框
            Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
            confirmAlert.setTitle("确认删除");
            confirmAlert.setHeaderText("确认删除订单");
            confirmAlert.setContentText(String.format("您确定要删除订单吗？\n" +
                            "菜名: %s\n" +
                            "单价: %.2f元\n" +
                            "数量: %d\n" +
                            "总价: %.2f元\n" +
                            "下单日期: %s",
                    selectedOrder.getName(),
                    selectedOrder.getPrice(),
                    selectedOrder.getNumber(),
                    selectedOrder.getTotalPrice(),
                    selectedOrder.getDate()));

            // 显示对话框并等待用户确认
            Optional<ButtonType> result = confirmAlert.showAndWait();

            if (result.isPresent() && result.get() == ButtonType.OK) { // 用户确认删除
                int orderId = selectedOrder.getId(); // 获取选择的订单ID
                double refundAmount = selectedOrder.getTotalPrice(); // 获取订单的总金额以进行退款
                System.out.println("Refund amount: " + refundAmount);
                // 从数据库中删除该订单
                try {
                    // 进行退款
                    refundUser(MainOrdering.CurrentID, refundAmount); // 调用退款方法

                    String sql = "DELETE FROM orders WHERE ID = ?";
                    PreparedStatement preparedStatement = MainOrdering.connection.prepareStatement(sql);
                    preparedStatement.setInt(1, orderId);
                    int rowsAffected = preparedStatement.executeUpdate();

                    // 检查删除是否成功
                    if (rowsAffected > 0) {
                        myOrderTableView.getItems().remove(selectedOrder); // 从TableView中移除该订单
                        totalPrice -= refundAmount; // 更新总价格
                        sumPriceLabel.setText(String.format("%.2f元", totalPrice));
                        MainOrdering.showAlert("提示", "订单删除成功，已退款" + refundAmount + "元。", AlertType.INFORMATION);
                        // 设置余额显示
                        balanceLabel.setText(String.format("%.2f元", MainOrdering.getBalance(MainOrdering.CurrentID, MainOrdering.CurrentType)));
                    } else {
                        MainOrdering.showAlert("错误", "订单删除失败，可能该订单不存在。", AlertType.ERROR);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    MainOrdering.showAlert("错误", "删除订单时发生异常！", AlertType.ERROR);
                }
            }
        } else {
            MainOrdering.showAlert("提示", "请选中要删除的订单。", Alert.AlertType.WARNING);
        }
    }


    // 进行退款的方法
    private void refundUser(int userId, double refundAmount) {
        try {
            // 获取当前余额
            double currentBalance = MainOrdering.getBalance(userId, MainOrdering.CurrentType);
            double newBalance = currentBalance + refundAmount; // 计算新余额

            // 更新用户余额
            MainOrdering.updateBalance(userId, newBalance);

        } catch (Exception e) {
            e.printStackTrace();
            MainOrdering.showAlert("错误", "退款过程中发生异常！", AlertType.ERROR);
        }
    }


    // 返回按钮的处理逻辑
    @FXML
    private void handleReturnButtonClicked() {
        Stage stage = (Stage) returnButton.getScene().getWindow();
        try {
            FXMLLoader loader;
            Parent newScene;

            // 跳转到用户页面
            loader = new FXMLLoader(getClass().getResource("user.fxml"));
            newScene = loader.load();
            Scene scene = new Scene(newScene);
            stage.setScene(scene);
            stage.setTitle("用户页面");

            stage.show(); // 显示新场景
        } catch (IOException e) {
            MainOrdering.showAlert("错误", "页面加载失败: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    public void setSinglePriceTableColumn(TableColumn<Order, Double> singlePriceTableColumn) {
        this.singlePriceTableColumn = singlePriceTableColumn;
    }
}
