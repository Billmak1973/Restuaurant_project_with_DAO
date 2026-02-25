
# 🍽️ 餐廳管理系統 (Restaurant Management System) 專案分析報告

## 1. 專案概述 (Project Overview)
這是一個基於 **Java Swing** 開發的桌面端餐廳管理系統。系統採用 **MVC (Model-View-Controller)** 架構設計，實現了從顧客排隊、餐桌管理、點餐服務、結帳收銀到營業報表的全流程業務邏輯。系統強調數據一致性，使用了事務管理來確保訂單、庫存和營收數據的準確性。

## 2. 技術棧 (Technology Stack)
*   **開發語言**: Java (JDK 8+)
*   **用戶界面 (UI)**: Java Swing (AWT)
*   **資料庫**: MySQL (透過 JDBC 連接)
*   **連接池**: HikariCP (`com.zaxxer.hikari`)
*   **圖表庫**: JFreeChart (`org.jfree.chart`)
*   **報表導出**: Apache POI (`org.apache.poi`)
*   **日期處理**: Java Time API (`java.time`)
*   **架構模式**: MVC + DAO (Data Access Object) + Observer (觀察者模式)

## 3. 系統架構 (System Architecture)
系統嚴格遵循分層架構，職責分明：

*   **View 層 (`com.restaurant.view`)**:
    *   `RestaurantView`: 主管理界面（餐桌可視化、隊列顯示、控制按鈕）。
    *   `OrderSystemGUI`: 點餐系統窗口（包含 HomePanel, MenuPanel）。
    *   負責 UI 渲染、用戶輸入驗證及事件轉發。
*   **Controller 層 (`com.restaurant.controller`)**:
    *   `RestaurantController`: 核心控制器，實現 `ModelChangeListener` 接口。
    *   負責接收 View 層事件，調用 Model 層業務邏輯，並更新 View 顯示。
    *   處理事務邊界協調（部分業務邏輯）。
*   **Model 層 (`com.restaurant.model`)**:
    *   `RestaurantModel`: 核心業務邏輯層，維護內存狀態（餐桌、隊列、顧客組）。
    *   實現複雜業務規則（如自動拼桌、拆分餐桌、隊列分配算法）。
    *   通過觀察者模式通知 View 層更新。
*   **DAO 層 (`com.restaurant.dao` & `impl`)**:
    *   負責所有數據庫交互（CRUD）。
    *   包含 `OrderDAO`, `TablesDAO`, `CustomerGroupDAO`, `QueueDAO` 等。
    *   支持事務連接傳遞，確保原子性操作。
*   **Entity 層 (`com.restaurant.entity`)**:
    *   數據實體類：`Tables`, `CustomerGroup`, `MenuItem`, `OrderItem` 等。
*   **Service 層 (`com.restaurant.service`)**:
    *   `ConnectionPool`: 數據庫連接池管理。
    *   `MenuCategoryService`: 菜單分類緩存服務。

## 4. 核心功能模塊 (Core Features)

### 4.1 餐桌管理
*   **可視化狀態**: 實時顯示餐桌狀態（空閒、占用、準備中、拆分中）。
*   **靈活操作**: 支持餐桌拆分（2/4 人桌拆分為子桌）、合併（相鄰桌合併）、換桌。
*   **狀態同步**: 離店後自動清理狀態，支持子桌合併恢復。

### 4.2 顧客與隊列管理
*   **排隊系統**: 分為 2 人、4 人、6 人隊列，自動分配叫號。
*   **智能入座**: 支持自動分配空桌、合併餐桌入座、共享餐桌（拼桌）。
*   **隊列維護**: 支持編輯隊列人數、刪除排隊顧客、手動分配隊列顧客。

### 4.3 點餐與訂單系統
*   **臨時訂單**: 支持加菜、減菜（取消），確認前可修改。
*   **正式訂單**: 確認下單後寫入數據庫，支持追加点單。
*   **上菜管理**: 支持標記單個菜品或全部菜品為“已上桌”。
*   **訂單撤銷**: 支持撤銷菜品（已上桌菜品需填寫原因）。

### 4.4 結帳與收銀
*   **結帳流程**: 驗證訂單狀態、計算總金額、輸入支付金額、自動計算找零。
*   **營收記錄**: 自動記錄每日營收、顧客數量到 `business_status` 表。
*   **跨日處理**: 支持跨日結帳，營收計入訂單創建日期。

### 4.5 報表與分析
*   **營業報表**: 支持單日及日期範圍查詢（營收、客流、客單價）。
*   **菜品銷售**: 季度菜品銷售分析（銷量、銷售額、平均單價）。
*   **數據可視化**: 集成 JFreeChart 生成柱狀圖、餅圖。
*   **導出功能**: 支持將報表導出為 Excel (.xlsx) 或 CSV 格式。

### 4.6 系統管理
*   **營業狀態**: 支持“開始營業”與“結束營業”切換，打烊時限制新顧客入座。
*   **菜單管理**: 支持添加新菜品、修改價格、停售/售罄狀態切換、物理刪除菜品。
*   **日誌記錄**: 實時記錄系統操作日誌。

## 5. 資料庫設計 (Database Schema)
系統初始化時會自動創建以下核心表（見 `ConnectionPool.initializeDatabaseSchema`）：

| 表名 | 描述 | 關鍵字段 |
| :--- | :--- | :--- |
| `restaurant_tables` | 餐桌信息 | `table_id`, `status`, `capacity`, `merged_with`, `is_split` |
| `customer_groups` | 顧客組信息 | `group_id`, `call_number`, `group_size`, `table_id` |
| `queues` | 排隊隊列 | `queue_type`, `group_id`, `position` |
| `table_orders` | 訂單主表 | `order_id`, `table_id`, `status`, `total_amount`, `is_checked_out` |
| `order_items` | 訂單明細 | `order_item_id`, `order_id`, `item_id`, `quantity`, `served_quantity` |
| `menu_items` | 菜單菜品 | `item_id`, `item_code`, `price`, `is_active` |
| `business_status` | 營業狀態 | `business_date`, `is_open`, `daily_revenue`, `next_call_number` |
| `item_quarterly_sales` | 季度銷售統計 | `item_code`, `year`, `quarter`, `quantity_sold` |
| `item_cancellations` | 撤銷記錄審計 | `item_code`, `cancellation_reason`, `cancellation_time` |

## 6. 專案結構 (Project Structure)
```text
src/
├── com/
│   └── restaurant/
│       ├── controller/      # 控制器 (RestaurantController)
│       ├── dao/             # DAO 接口與實現 (impl/)
│       ├── entity/          # 實體類 (Tables, OrderItem, etc.)
│       ├── model/           # 業務模型 (RestaurantModel)
│       ├── service/         # 服務層 (ConnectionPool, MenuCategoryService)
│       ├── util/            # 工具類 (ConfigLoader, OperationResult)
│       ├── view/            # 視圖層 (Swing UI)
│       └── Restaurant.java  # 程式入口 (Main)
├── database.properties      # 資料庫配置文件 (可選，有默認值)
└── lib/                     # 依賴庫 (HikariCP, POI, JFreeChart, MySQL Driver)
```

## 7. 安裝與運行指南 (Installation & Usage)

### 7.1 環境要求
*   Java Development Kit (JDK) 1.8 或更高版本
*   MySQL 5.7 或 8.0+
*   Maven (可選，用於依賴管理) 或 手動導入 Jar 包

### 7.2 依賴庫
需確保 classpath 中包含以下庫：
*   `mysql-connector-java.jar`
*   `HikariCP.jar`
*   `poi-ooxml.jar` & `poi.jar`
*   `jfreechart.jar`

### 7.3 配置資料庫
系統默認嘗試連接本地 MySQL，可通過 `database.properties` 文件或修改 `ConfigLoader` 中的默認值進行配置：
```properties
database.url=jdbc:mysql://localhost:3306/
database.username=root
database.password=1234
database.name=restaurant_init_db
```

### 7.4 啟動系統
運行主類 `com.restaurant.Restaurant` 的 `main` 方法。
*   首次運行時，系統會自動初始化數據庫結構並插入默認餐桌數據。
*   默認創建 15 張餐桌（1-6 號為 2 人桌，7-12 號為 4 人桌，13-15 號為 6 人桌）。

## 8. 關鍵設計亮點 (Key Design Highlights)
*   **事務安全性**: 關鍵業務（如結帳、換桌、拆分）均使用 `Connection` 事務控制，確保數據庫與內存狀態一致，支持回滾。
*   **內存緩存**: `RestaurantModel` 維護內存狀態（餐桌地圖、隊列），減少數據庫查詢頻率，提高 UI 響應速度。
*   **觀察者模式**: Model 層狀態變更通過 `ModelChangeListener` 通知 Controller，再由 Controller 調度 View 更新，實現解耦。
*   **並發處理**: 使用 `SwingWorker` 處理耗時數據庫操作，避免阻塞 EDT (Event Dispatch Thread)；使用 `ConcurrentHashMap` 和 `synchronized` 確保線程安全。
*   **健壯性設計**: 包含詳細的輸入驗證、異常處理機制，以及數據庫連接池管理，防止資源洩漏。

---

## 📝 建議的 README.md 模板

您可以直接複製以下內容作為您的 `README.md` 基礎：

```markdown
# 🍽️ 餐廳管理系統 (Restaurant Management System)

一個基於 Java Swing 和 MySQL 開發的桌面端餐廳管理系統，涵蓋了從排隊、點餐、結帳到數據分析的全流程業務。

## ✨ 主要功能

- **餐桌管理**: 可視化餐桌狀態，支持拆分、合併、換桌及清理。
- **智能排隊**: 按人數自動分類隊列（2/4/6 人），支持叫號入座。
- **點餐系統**: 臨時訂單編輯、正式下單、菜品上桌標記、撤銷菜品。
- **結帳收銀**: 自動計算總額與找零，支持跨日結帳統計。
- **數據報表**: 營業日報、範圍查詢、季度菜品銷售分析（圖表 + Excel 導出）。
- **菜單管理**: 菜品增刪改查、價格調整、售罄狀態管理。

## 🛠 技術棧

- **語言**: Java 8+
- **UI**: Java Swing
- **資料庫**: MySQL + JDBC
- **連接池**: HikariCP
- **圖表**: JFreeChart
- **導出**: Apache POI

## 🚀 快速開始

### 1. 環境準備
確保已安裝 JDK 和 MySQL 數據庫。

### 2. 配置資料庫
修改根目錄下的 `database.properties` 文件（或依賴默認配置）：
```properties
database.url=jdbc:mysql://localhost:3306/
database.username=root
database.password=your_password
database.name=restaurant_db
```

### 3. 運行專案
執行主類 `com.restaurant.Restaurant`。
*首次運行會自動初始化數據庫表結構和默認餐桌數據。*

## 📂 專案結構

```
src/com/restaurant/
├── controller/   # 控制層
├── model/        # 業務邏輯層
├── view/         # 界面層
├── dao/          # 數據訪問層
├── entity/       # 實體類
└── service/      # 服務層 (連接池等)
```

## 📸 系統截圖
<img width="1918" height="1011" alt="image" src="https://github.com/user-attachments/assets/12d70674-584d-4340-b94d-8939dde29e1a" />
<img width="1102" height="862" alt="image" src="https://github.com/user-attachments/assets/234f9a4f-7b6f-4b98-ba8b-90261bd7b6fc" />


## 📄 許可證

本專案僅供學習與研究使用。
```

---

### 💡 給您的建議
1.  **補充截圖**: README 中最重要的是視覺效果，建議運行項目後截取 3-4 張關鍵界面圖（主界面、點餐界面、報表界面）。
2.  **依賴說明**: 如果您是使用 Maven 管理依賴，建議附上 `pom.xml` 片段；如果是手動導包，請說明需要哪些 Jar 包。
3.  **數據庫腳本**: 雖然代碼中有自動初始化邏輯，但若能提供一份獨立的 `.sql` 腳本文件會更專業。
4.  **已知問題**: 如果有尚未完成的功能或已知 Bug，可以在 README 底部誠實標註，體現專業性。
