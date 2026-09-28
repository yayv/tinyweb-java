当前文件读取流程

HTTP 请求
  ↓
/static/index?f=showDemo
  ↓
Static 控制器
  ↓
JarFileServe 模型
  ↓
jar 包内的 static_pages/showDemo.html
  ↓
返回 HTTP 响应

项目生成流程

java -jar tinyweb.jar gen-project /path/to/project com.example.app
  ↓
Tools.genProject()
  ↓
readTemplateFile() 从 jar 包读取 templates/demo1/...
  ↓
替换包名、写到目标目录

现在静态文件和模板都通过 jar 包内的资源读取，不需要解压，更加优雅高效。