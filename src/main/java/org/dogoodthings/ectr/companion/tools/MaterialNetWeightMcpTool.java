package org.dogoodthings.ectr.companion.tools;

import java.util.ArrayList;
import java.util.List;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import com.dscsag.plm.spi.interfaces.ECTRService;
import com.dscsag.plm.spi.interfaces.ai.tools.Tool;
import com.dscsag.plm.spi.interfaces.rfc.RfcResult;
import com.dscsag.plm.spi.interfaces.rfc.RfcStructure;
import com.dscsag.plm.spi.rfc.builder.RfcCallBuilder;

@Component (service = MaterialNetWeightMcpTool.class)
public class MaterialNetWeightMcpTool
{
  @Reference ECTRService ectr;

  private static RfcStructure exportStructure(RfcResult result, String name)
  {
    var parameter = result.getExportParameter(name);
    return parameter != null && parameter.isStructure() ? parameter.getStructure() : null;
  }

  @Tool (value = "Reads NET_WEIGHT and UNIT_OF_WT for SAP material (MARA) keys using BAPI_MATERIAL_GET_DETAIL. Supply SAP material numbers in their internal format, preserving leading zeros. Returns successful materials and separate per-key errors. Does not modify SAP data.", name = "material_net_weights")
  public MatNetWeightResult call(List<String> maraKeys)
  {
    if (maraKeys.isEmpty())
      throw new IllegalArgumentException("No mara keys supplied");

    List<String> materialKeys = new ArrayList<>();
    for (Object key : maraKeys)
    {
      if (!(key instanceof String materialKey) || materialKey.isBlank())
        throw new IllegalArgumentException("Invalid arguments: every MARA key must be a non-blank string.");
      materialKeys.add(materialKey);
    }

    List<MatNetWeight> materials = new ArrayList<>();
    List<MatError> errors = new ArrayList<>();
    ectr.getPlmLogger().verbose("MaterialNetWeightMcpTool reading " + materialKeys.size() + " materials");
    for (String key : materialKeys)
    {
      RfcCallBuilder builder = new RfcCallBuilder("BAPI_MATERIAL_GET_DETAIL");
      builder.setInputParameter("MATERIAL", key);
      RfcResult result = ectr.getRfcExecutor().execute(builder.toRfcCall());
      if (!result.isSuccessful())
      {
        String message = result.getErrorMessage();
        errors.add(new MatError(key,
            message == null || message.isBlank() ? "BAPI_MATERIAL_GET_DETAIL failed without an error message." : message));
        continue;
      }

      // RFC execution can succeed even when the BAPI reports an application error.
      RfcStructure returnData = exportStructure(result, "RETURN");
      if (returnData != null)
      {
        String type = returnData.getFieldValue("TYPE");
        if (type != null && List.of("E", "A", "X").contains(type.trim()))
        {
          String message = returnData.getFieldValue("MESSAGE");
          errors.add(new MatError(key,
              message == null || message.isBlank() ? "BAPI_MATERIAL_GET_DETAIL returned error type " + type : message));
          continue;
        }
      }

      RfcStructure data = exportStructure(result, "MATERIAL_GENERAL_DATA");
      String weight = data == null ? null : data.getFieldValue("NET_WEIGHT");
      String unit = data == null ? null : data.getFieldValue("UNIT_OF_WT");
      if (weight == null || weight.isBlank() || unit == null || unit.isBlank())
      {
        errors.add(new MatError(key, "SAP did not return NET_WEIGHT and UNIT_OF_WT."));
        continue;
      }
      materials.add(new MatNetWeight(key, weight, unit));
    }

    return new MatNetWeightResult(List.copyOf(materials), List.copyOf(errors));
  }

  public record MatNetWeight(String matNumber, String netWeight, String unit)
  {
  }

  public record MatError(String matNumber, String error)
  {
  }

  public record MatNetWeightResult(List<MatNetWeight> materials, List<MatError> errors)
  {
  }
}
