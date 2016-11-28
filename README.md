# General Node

Nó representando um General no problema dos Generais Bizantinos.

## Execução

Para executar o programa primeiramente faça o build do mesmo na raiz do projeto

~~~ sh
$ mvn clean install 
~~~

Depois rode o Jar autoexecutavel

~~~ sh
$ java -jar target/general-node-exec-1.0-SNAPSHOT.jar general_privatekey
~~~

Onde general_privatekey é uma chave secreta gerada por [Keypair Generator](https://github.com/marcelobaxauli/KeypairGenerator)
