import com.restaurant.controller.RestaurantController;
import com.restaurant.model.RestaurantModel;
import com.restaurant.service.ConnectionPool;
import com.restaurant.view.OrderSystemGUI;
import com.restaurant.view.RestaurantView;

import javax.swing.*;

public class Restaurant {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {

            ConnectionPool.initializeDatabaseSchema();
            // 1. 初始化连接池 (必须在任何数据库操作前完成)
            ConnectionPool.initializePool();

            // 2. 创建模型 (模型内部会处理数据库初始化和数据加载)
            RestaurantModel model = new RestaurantModel();
            OrderSystemGUI orderFrame = new OrderSystemGUI(null, model);
            orderFrame.setVisible(false); // 初始隐藏
            // 3. 创建视图
            RestaurantView view = new RestaurantView();

            // 4. 创建控制器 (连接模型和视图)
            RestaurantController controller = new RestaurantController(model, view,orderFrame);

            orderFrame = new OrderSystemGUI(controller, model);
            orderFrame.setVisible(false);

            view.setController(controller);

            // 5. 显示主窗口
            view.setVisible(true);
        });
    }
}