package sample;

import static com.oculusvr.capi.OvrLibrary.ovrHmdType.ovrHmd_DK2;
import static com.oculusvr.capi.OvrLibrary.ovrTrackingCaps.ovrTrackingCap_Orientation;
import static com.oculusvr.capi.OvrLibrary.ovrTrackingCaps.ovrTrackingCap_Position;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.oculusvr.capi.Hmd;
import com.oculusvr.capi.OvrQuaternionf;
import com.oculusvr.capi.OvrVector3f;
import com.oculusvr.capi.Posef;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Point3D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.SubScene;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * @author Kiyotaka Oku
 */
public class App extends Application {

    private static final double WIDTH = 1920;
    private static final double HEIGHT = 1080;

    private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
    private Hmd hmd;

    private void setupHmd() {

        Hmd.initialize();

        try {
            Thread.sleep(400);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }

        hmd = Hmd.create(0);
        if (null == hmd) {
            hmd = Hmd.createDebug(ovrHmd_DK2);
        }

        if (null == hmd) {
            throw new IllegalStateException("Unable to initialize HMD");
        }

        if (0 == hmd.configureTracking(ovrTrackingCap_Orientation | ovrTrackingCap_Position, 0)) {
            throw new IllegalStateException("Unable to start the sensor");
        }
    }

    private void shutdownHmd() {
        hmd.destroy();
        Hmd.shutdown();
    }

    @Override
    public void start(Stage primaryStage) throws Exception {

        setupHmd();

        HBox root = new HBox();

        /*
         * 左側(3Dを描画)
         */
        Group parent3d = new Group();

        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setNearClip(0);
        camera.setFarClip(100);
        camera.setFieldOfView(45);

        Box box = new Box(0.1, 0.1, 0.1);
        box.setMaterial(new PhongMaterial(Color.BLUE));
        box.setTranslateZ(0.5);

        parent3d.getChildren().addAll(camera, box);

        SubScene subScene = new SubScene(parent3d, WIDTH / 2, HEIGHT);
        subScene.setCamera(camera);

        /*
         * 右側(スナップショット)
         */
        ImageView snapshotView = new ImageView();
        StackPane snapshotPane = new StackPane(snapshotView);
        snapshotPane.setPrefSize(WIDTH / 2, HEIGHT / 2);

        root.getChildren().addAll(subScene, snapshotPane);

        Runnable snapshot = () -> {
            SnapshotParameters params = new SnapshotParameters();
            params.setCamera(camera); // ちょっと位置をずらしたほうがいいと思う。
            params.setDepthBuffer(true);
            params.setViewport(new Rectangle2D(0, 0, subScene.getWidth(), subScene.getHeight()));
            snapshotView.setImage(subScene.getRoot().snapshot(params, null));
        };

        snapshot.run();

        /*
         * センサから値をとってスナップショットを更新し続ける
         */
        executorService.scheduleWithFixedDelay(() -> {
            Posef eyePose = hmd.getEyePose(0);
            OvrQuaternionf rotation = eyePose.Orientation;
            OvrVector3f position = eyePose.Position;
            double rad = 2 * Math.acos(rotation.w);

            Platform.runLater(() -> {
                camera.setTranslateX(position.x);
                camera.setTranslateY(-position.y);
                camera.setTranslateZ(-position.z);
                camera.setRotate((rad * 180 / Math.PI));
                camera.setRotationAxis(new Point3D(
                        rotation.x / Math.sin(rad / 2),
                        -rotation.y / Math.sin(rad / 2),
                        -rotation.z / Math.sin(rad / 2)));
                snapshot.run();
            });
        }, 100, 10, TimeUnit.MILLISECONDS);

        Scene scene = new Scene(root);

        primaryStage.setOnCloseRequest((e) -> {
            executorService.shutdownNow();
            shutdownHmd();
        });
        primaryStage.setX(0);
        primaryStage.setY(0);
        primaryStage.initStyle(StageStyle.UNDECORATED);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
