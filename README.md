# TCPMessenger

TCPMessenger will handle the TCP communication and cache between a socket server and the Android application,
but also will provide type-safe response defined by a user in the MainThread to facilitate UI update.

# How to
```java
public class MainActivity extends AppCompatActivity implements TCPMessenger.Callback<String> {

    private ProgressDialog progressDialog;
    private Button button;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        button = findViewById(R.id.button_test);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (progressDialog == null) {
                    progressDialog = new ProgressDialog(MainActivity.this);
                    progressDialog.setIndeterminate(true);
                    progressDialog.setMessage("Processing...");
                }

                progressDialog.show();
                TCPMessenger
                        .getDefaultInstance()
                        .sendCommand(new TCPMessenger.Request("192.168.1.1", "Hello"), String.class, MainActivity.this);
            }
        });


    }

    @Override
    public void onResponse(TCPMessenger.Request request, String s) {
        progressDialog.dismiss();
        Toast.makeText(this, "Response: " + s + " for request: " + request, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onError(TCPMessenger.Request request, Throwable throwable) {
        progressDialog.dismiss();
        Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
    }
}
```


# Download

Add it in your root build.gradle at the end of repositories:

```groovy
	allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
```

now add  the dependency on your project build.gradle file
```groovy
compile 'com.github.murielK:TCPMessenger:1.0.0'
```

# License

> Licensed under the Apache License, Version 2.0 (the "License");
> you may not use this work except in compliance with the License.
> You may obtain a copy of the License in the LICENSE file, or at:
>
>  [http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)
>
> Unless required by applicable law or agreed to in writing, software
> distributed under the License is distributed on an "AS IS" BASIS,
> WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
> See the License for the specific language governing permissions and
> limitations under the License.


