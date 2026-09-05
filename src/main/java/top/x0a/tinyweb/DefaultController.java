package top.x0a.tinyweb;

/** DefaultController — 对照 c/defaultcontroller.php 的兜底控制器。 */
public class DefaultController extends Controller {
    @Override
    public void index() {
        echo("tinyWEB-java: default controller\n");
    }
}
