# Projeto PDV Kiosk Android

Este projeto é um aplicativo Android nativo desenvolvido em Kotlin para funcionar como um Kiosk WebView.

## Funcionalidades
1.  **Configuração Inicial**: Permite definir a URL do servidor no primeiro uso.
2.  **Persistência**: Lembra a URL para as próximas vezes.
3.  **Kiosk Mode**: Tela cheia, sem barra de URL.
4.  **Reset**: Pressionar e segurar o botão VOLTAR por 2 segundos permiter redefinir a URL.
5.  **Notificações Nativas**: Integração via Javascript Bridge para exibir notificações do sistema.

## Como Compilar e Rodar

### Pré-requisitos
- Android Studio instalado (versão recente, Hedgehog ou superior).
- JDK 17 configurado no Android Studio.

### Passos
1.  Abra o Android Studio.
2.  Selecione **Open** e navegue até a pasta deste projeto: `C:\Users\igorp\Nextcloud\Projeto_PDV_Android`.
3.  Aguarde o Gradle sincronizar (o Android Studio baixará as dependências necessárias).
4.  Conecte seu dispositivo Android via USB (com Depuração USB ativada) ou crie um Emulador.
5.  Clique no botão **Run** (ícone de Play verde).

### Opção 2: Compilação via GitHub Actions (Sem Android Studio)
Como este projeto já possui a configuração de CI/CD (`.github/workflows/android.yml`), você pode gerar o APK na nuvem:

1.  Crie um novo repositório no GitHub.
2.  Envie todos os arquivos desta pasta para o repositório (`git init`, `git add .`, `git commit`, `git push`).
3.  No GitHub, vá até a aba **Actions**.
4.  Você verá um workflow rodando (nome "Android Build").
5.  Quando terminar (ficar verde), clique nele.
6.  Role a página até o fim em **Artifacts**.
7.  Baixe o arquivo `debug-apk`. Ele virá zipado. Extraia e instale o APK no seu celular.

## Notas sobre Notificações
O App possui uma "ponte" para notificações.
O site do PDV deve chamar `new Notification("Titulo", {body: "Mensagem"})` no Javascript.
O App intercepta isso e exibe uma notificação nativa do Android.

**Permissões**: No Android 13+, o app pedirá permissão na primeira execução.

## Estrutura de Arquivos Importantes
- `app/src/main/AndroidManifest.xml`: Permissões.
- `app/src/main/java/com/example/kioskpdv/MainActivity.kt`: Lógica principal.
- `app/src/main/java/com/example/kioskpdv/WebAppInterface.kt`: Ponte de notificações.
