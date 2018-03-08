# 简介
 - android-architecture是官方推出的一个应用开发架构指引，用来引导开发者开发可测性，维护性，扩展性更高的应用．里面以一个简单项目的多种不同架构实现方式来展示如何完成一个应用．
 - 作为这个架构指引学习的第一个项目，我先选了一个最简单的todo-mvp.这个项目以一个不使用任何架构组件的MVP架构来实现一个简单的任务管理应用．后续的其他几个项目也是基于这个项目以引入其他组件的方式（例如使用依赖注入框架Dagger或者RxJava）做的变更．
 - https://github.com/googlesamples/android-architecture

# 应用的界面
整个应用由以下四个界面构成：
 - 任务列表（Tasks）：一个被管理任务的列表界面
 - 任务详情（TaskDetail）：用来阅读或删除任务的界面
 - 任务编辑（AddEditTask）：新建或便利任务的界面
 - 分析界面（Statistics）：展示与任务有关的分析

# 界面构成
以上四个界面，由以下几个类和接口构成：
 - 契约类（xxContract）：用来定义MVP中View和Presenter的接口，每一个界面做了什么事情能展示什么内容这里都一目了然．
 - Activity：就是android的界面组件，在这里几个项目里，它负责创建Fragment和Presenter
 - Fragment：实现MVP中的View
 - Presenter：实现MVP中的P
其中，presenter负责业务逻辑．view则负责UI展现和将用户的交互操作转发给presenter，内部不包括任何逻辑.

# 项目的概要概要
![N|Solid](https://raw.githubusercontent.com/kingonlive/WildChild/master/todo-mvp/todo-mvp-arch.png)

# 代码组织结构
```
.
└── com
    └── example
        └── android
            └── architecture
                └── blueprints
                    └── todoapp
                        ├── addedittask
                        │   ├── AddEditTaskActivity.java
                        │   ├── AddEditTaskContract.java
                        │   ├── AddEditTaskFragment.java
                        │   └── AddEditTaskPresenter.java
                        ├── BasePresenter.java
                        ├── BaseView.java
                        ├── data
                        │   ├── source
                        │   │   ├── local
                        │   │   │   ├── TasksDbHelper.java
                        │   │   │   ├── TasksLocalDataSource.java
                        │   │   │   └── TasksPersistenceContract.java
                        │   │   ├── remote
                        │   │   │   └── TasksRemoteDataSource.java
                        │   │   ├── TasksDataSource.java
                        │   │   └── TasksRepository.java
                        │   └── Task.java
                        ├── statistics
                        │   ├── StatisticsActivity.java
                        │   ├── StatisticsContract.java
                        │   ├── StatisticsFragment.java
                        │   └── StatisticsPresenter.java
                        ├── taskdetail
                        │   ├── TaskDetailActivity.java
                        │   ├── TaskDetailContract.java
                        │   ├── TaskDetailFragment.java
                        │   └── TaskDetailPresenter.java
                        ├── tasks
                        │   ├── ScrollChildSwipeRefreshLayout.java
                        │   ├── TasksActivity.java
                        │   ├── TasksContract.java
                        │   ├── TasksFilterType.java
                        │   ├── TasksFragment.java
                        │   └── TasksPresenter.java
                        └── util
                            ├── ActivityUtils.java
                            ├── EspressoIdlingResource.java
                            └── SimpleCountingIdlingResource.java

15 directories, 30 files
```

# 实现类图

# 关于自动化测试
在该项目中，presenter/model/UI界面，都是能够测试的，他们的测试代码存在与以下文件夹中：
 - test目录：这里是纯Java代码的单元测试代码
 - androidTest：这里是依赖于android framework（需要真实设备或者模拟器）的单元测试代码
 - AndroidTestMock：这里是基于Esoresso这个UI自动化测试框架编写的单元测试
 - prd/mock：项目中使用Gradle支持的product flavors让我们能够在运行时替换某个模块．由于自动化测试中有些环境或代码的行为表现无法和真实环境相同，因此利用product flavors，来在运行时替换成我们模拟的数据或者行为，以便自动化测试顺利进行.
