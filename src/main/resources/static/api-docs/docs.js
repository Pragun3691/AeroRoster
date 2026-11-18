const container = document.querySelector("#operations");
const filter = document.querySelector("#filter");

function section(parent, title, value) {
  const heading = document.createElement("h2");
  heading.textContent = title;
  const content = document.createElement("pre");
  content.textContent = JSON.stringify(value, null, 2);
  parent.append(heading, content);
}

async function loadDocumentation() {
  try {
    const response = await fetch("../api/v1/openapi.json");
    if (!response.ok) throw new Error("Specification unavailable");
    const spec = await response.json();
    container.replaceChildren();
    for (const [path, methods] of Object.entries(spec.paths)) {
      for (const [method, operation] of Object.entries(methods)) {
        const entry = document.createElement("details");
        const summary = document.createElement("summary");
        summary.textContent = `${method.toUpperCase()} /api/v1${path} — ${operation.summary}`;
        entry.append(summary);
        if (operation.description) {
          const description = document.createElement("p");
          description.textContent = operation.description;
          entry.append(description);
        }
        section(entry, "Parameters", operation.parameters);
        if (operation.requestBody) section(entry, "Request", operation.requestBody);
        section(entry, "Responses", operation.responses);
        container.append(entry);
      }
    }
    document.querySelector("#schemas").textContent = JSON.stringify(spec.components.schemas, null, 2);
  } catch (error) {
    container.textContent = "The API specification could not be loaded. Start AeroRoster and reload this page.";
  }
}

filter.addEventListener("input", () => {
  const query = filter.value.toLowerCase();
  for (const entry of container.querySelectorAll("details")) {
    entry.hidden = !entry.querySelector("summary").textContent.toLowerCase().includes(query);
  }
});

loadDocumentation();
