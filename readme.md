# Camunda External Task Dispatcher
Camunda External Task Dispatcher is a kind of middleware that helps you to execute [external service tasks](https://docs.camunda.org/manual/7.7/user-guide/process-engine/external-tasks/) of [Camunda PBM](https://camunda.org/) in asynchronous manner.
## Design
Take a look at [component design sheet](./docs/design/component-design.md)
## Getting started
### Dependencies
#### External task processor service
- camunda-task-dispatcher-processor
- camunda-task-dispatcher-receiver-jms
- camunda-task-dispatcher-completer-jms
- camunda-task-dispatcher-mapper-json
#### External task dispatcher service
- camunda-task-dispatcher-runtime
- camunda-task-dispatcher-transport-jms
- camunda-task-dispatcher-mapper-json
## Examples
You could find some examples [in co-named dir](./examples). 
## License
Camunda External Task Dispatcher is Open Source Software released under the [Apache 2.0 license](http://www.apache.org/licenses/LICENSE-2.0.html).