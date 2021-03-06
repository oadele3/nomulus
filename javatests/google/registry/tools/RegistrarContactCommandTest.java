// Copyright 2017 The Nomulus Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package google.registry.tools;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.registrar.RegistrarContact.Type.ABUSE;
import static google.registry.model.registrar.RegistrarContact.Type.ADMIN;
import static google.registry.model.registrar.RegistrarContact.Type.WHOIS;
import static google.registry.testing.DatastoreHelper.persistResource;
import static google.registry.testing.DatastoreHelper.persistSimpleResource;
import static google.registry.testing.DatastoreHelper.persistSimpleResources;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import google.registry.model.registrar.Registrar;
import google.registry.model.registrar.RegistrarContact;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Test;

/** Unit tests for {@link RegistrarContactCommand}. */
public class RegistrarContactCommandTest extends CommandTestCase<RegistrarContactCommand> {

  private String output;

  @Before
  public void before() throws Exception {
    output = tmpDir.newFile().toString();
  }

  @Test
  public void testList() throws Exception {
    Registrar registrar = Registrar.loadByClientId("NewRegistrar");
    RegistrarContact.updateContacts(
        registrar,
        ImmutableSet.of(
            new RegistrarContact.Builder()
                .setParent(registrar)
                .setName("John Doe")
                .setEmailAddress("john.doe@example.com")
                .setTypes(ImmutableSet.of(ADMIN))
                .setVisibleInWhoisAsAdmin(true)
                .build()));
    runCommandForced("--mode=LIST", "--output=" + output, "NewRegistrar");
    assertThat(Files.readAllLines(Paths.get(output), UTF_8))
        .containsExactly(
            "John Doe",
            "john.doe@example.com",
            "Types: [ADMIN]",
            "Visible in WHOIS as Admin contact: Yes",
            "Visible in WHOIS as Technical contact: No");
  }

  @Test
  public void testUpdate() throws Exception {
    Registrar registrar = Registrar.loadByClientId("NewRegistrar");
    ImmutableList<RegistrarContact> contacts = ImmutableList.of(
        new RegistrarContact.Builder()
            .setParent(registrar)
            .setName("Judith Doe")
            .setEmailAddress("judith.doe@example.com")
            .setTypes(ImmutableSet.of(WHOIS))
            .setVisibleInWhoisAsAdmin(true)
            .setVisibleInWhoisAsTech(true)
            .build());
    persistSimpleResources(contacts);
    runCommandForced(
        "--mode=UPDATE",
        "--name=Judith Registrar",
        "--email=judith.doe@example.com",
        "--phone=+1.2125650000",
        "--fax=+1.2125650001",
        "--contact_type=WHOIS",
        "--visible_in_whois_as_admin=true",
        "--visible_in_whois_as_tech=true",
        "NewRegistrar");
    RegistrarContact registrarContact =
        Registrar.loadByClientId("NewRegistrar").getContacts().asList().get(1);
    assertThat(registrarContact).isEqualTo(
        new RegistrarContact.Builder()
            .setParent(registrar)
            .setName("Judith Registrar")
            .setEmailAddress("judith.doe@example.com")
            .setPhoneNumber("+1.2125650000")
            .setFaxNumber("+1.2125650001")
            .setTypes(ImmutableSet.of(WHOIS))
            .setVisibleInWhoisAsAdmin(true)
            .setVisibleInWhoisAsTech(true)
            .build());
  }

  @Test
  public void testUpdate_enableConsoleAccess() throws Exception {
    Registrar registrar = Registrar.loadByClientId("NewRegistrar");
    persistSimpleResource(
        new RegistrarContact.Builder()
            .setParent(registrar)
            .setName("Jane Doe")
            .setEmailAddress("jane.doe@example.com")
            .build());
    runCommandForced(
        "--mode=UPDATE",
        "--email=jane.doe@example.com",
        "--allow_console_access=true",
        "NewRegistrar");
    RegistrarContact registrarContact =
        Registrar.loadByClientId("NewRegistrar").getContacts().asList().get(1);
    assertThat(registrarContact.getGaeUserId()).matches("-?[0-9]+");
  }

  @Test
  public void testUpdate_disableConsoleAccess() throws Exception {
    Registrar registrar = Registrar.loadByClientId("NewRegistrar");
    persistSimpleResource(
        new RegistrarContact.Builder()
            .setParent(registrar)
            .setName("Judith Doe")
            .setEmailAddress("judith.doe@example.com")
            .setGaeUserId("11111")
            .build());
    runCommandForced(
        "--mode=UPDATE",
        "--email=judith.doe@example.com",
        "--allow_console_access=false",
        "NewRegistrar");
    RegistrarContact registrarContact =
        Registrar.loadByClientId("NewRegistrar").getContacts().asList().get(1);
    assertThat(registrarContact.getGaeUserId()).isNull();
  }

  @Test
  public void testCreate_withAdminType() throws Exception {
    Registrar registrar = Registrar.loadByClientId("NewRegistrar");
    runCommandForced(
        "--mode=CREATE",
        "--name=Jim Doe",
        "--email=jim.doe@example.com",
        "--contact_type=ADMIN,ABUSE",
        "--visible_in_whois_as_admin=true",
        "--visible_in_whois_as_tech=true",
        "NewRegistrar");
    RegistrarContact registrarContact =
        Registrar.loadByClientId("NewRegistrar").getContacts().asList().get(1);
    assertThat(registrarContact).isEqualTo(
        new RegistrarContact.Builder()
            .setParent(registrar)
            .setName("Jim Doe")
            .setEmailAddress("jim.doe@example.com")
            .setTypes(ImmutableSet.of(ADMIN, ABUSE))
            .setVisibleInWhoisAsAdmin(true)
            .setVisibleInWhoisAsTech(true)
            .build());
    assertThat(registrarContact.getGaeUserId()).isNull();
  }

  @Test
  public void testDelete() throws Exception {
    runCommandForced(
        "--mode=DELETE",
        "--email=janedoe@theregistrar.com",
        "NewRegistrar");
    assertThat(Registrar.loadByClientId("NewRegistrar").getContacts()).isEmpty();
  }

  @Test
  public void testCreate_withConsoleAccessEnabled() throws Exception {
    runCommandForced(
        "--mode=CREATE",
        "--name=Jim Doe",
        "--email=jim.doe@example.com",
        "--allow_console_access=true",
        "--contact_type=ADMIN,ABUSE",
        "NewRegistrar");
    RegistrarContact registrarContact =
        Registrar.loadByClientId("NewRegistrar").getContacts().asList().get(1);
    assertThat(registrarContact.getGaeUserId()).matches("-?[0-9]+");
  }

  @Test
  public void testCreate_syncingRequiredSetToTrue() throws Exception {
    persistResource(
        Registrar.loadByClientId("NewRegistrar")
            .asBuilder()
            .setContactsRequireSyncing(false)
            .build());

    assertThat(Registrar.loadByClientId("NewRegistrar").getContactsRequireSyncing()).isFalse();
    runCommandForced(
        "--mode=CREATE",
        "--name=Jim Doe",
        "--email=jim.doe@example.com",
        "NewRegistrar");
    assertThat(Registrar.loadByClientId("NewRegistrar").getContactsRequireSyncing()).isTrue();
  }
}
