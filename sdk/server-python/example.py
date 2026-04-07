from wmpp_server_sdk import WmppServerClient
import os


def main():
    client = WmppServerClient(
        base_url=os.getenv("WMPP_BASE_URL", "http://localhost:8082"),
        app_id="systemA",
        app_secret="systemA-secret",
    )

    print(client.broadcast("hello from python sdk"))
    print(client.push_user("1001", "hello 1001 from python sdk"))
    print(client.push_users(["1001", "2002"], "hello batch from python sdk"))
    print(client.push_topic("starA", "hello topic from python sdk"))


if __name__ == "__main__":
    main()

